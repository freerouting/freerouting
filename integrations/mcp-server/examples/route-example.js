const fs = require('fs');
const http = require('http');
const path = require('path');

// Default target MCP server URL (running locally or bridged)
const MCP_URL = process.env.FREEROUTING_MCP_URL || 'http://127.0.0.1:37964/v1/mcp';

function sendJsonRpc(payload) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(payload);
    const req = http.request(MCP_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
        // Identify client metadata headers
        'Freerouting-Profile-ID': '00000000-0000-0000-0000-000000000000',
        'Freerouting-Profile-Email': 'mcp-npx-client@local.freerouting.app',
        'Freerouting-Environment-Host': 'MCP-Example-Client/1.0'
      }
    }, (res) => {
      let body = '';
      res.on('data', chunk => body += chunk);
      res.on('end', () => {
        if (res.statusCode === 204) {
          resolve(null); // Notification response
          return;
        }
        try {
          const parsed = JSON.parse(body);
          if (parsed.error) {
            reject(new Error(`MCP error: ${JSON.stringify(parsed.error)}`));
          } else {
            resolve(parsed.result);
          }
        } catch (e) {
          reject(new Error(`Failed to parse response: ${body}`));
        }
      });
    });

    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function callTool(name, args = {}) {
  const result = await sendJsonRpc({
    jsonrpc: '2.0',
    id: Date.now() + '-' + Math.random(),
    method: 'tools/call',
    params: {
      name: name,
      arguments: args
    }
  });
  if (result.isError) {
    throw new Error(`Tool ${name} returned error: ${JSON.stringify(result)}`);
  }
  return result;
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length < 1) {
    console.log('Usage: node route-example.js <path-to-dsn-file> [path-to-output-ses-file]');
    process.exit(1);
  }

  const dsnPath = path.resolve(args[0]);
  const defaultOutputPath = dsnPath.replace(/\.dsn$/i, '.ses');
  const outputPath = args[1] ? path.resolve(args[1]) : defaultOutputPath;

  console.log(`Reading DSN file: ${dsnPath}`);
  if (!fs.existsSync(dsnPath)) {
    console.error(`Error: File not found at ${dsnPath}`);
    process.exit(1);
  }
  const dsnContent = fs.readFileSync(dsnPath);
  const dsnBase64 = dsnContent.toString('base64');

  console.log('Handshaking with MCP server...');
  const initRes = await sendJsonRpc({
    jsonrpc: '2.0',
    id: 'init-1',
    method: 'initialize',
    params: {
      clientInfo: { name: 'route-example-client', version: '1.0' },
      protocolVersion: '2024-11-05'
    }
  });
  console.log('Initialize response received:', JSON.stringify(initRes));

  await sendJsonRpc({
    jsonrpc: '2.0',
    method: 'notifications/initialized',
    params: {}
  });
  console.log('Handshake complete.');

  console.log('1. Creating session...');
  const createSessionRes = await callTool('create_session');
  const sessionInfo = JSON.parse(createSessionRes.content[0].text);
  const sessionId = sessionInfo.body.id || sessionInfo.body;
  console.log(`Session created: ${sessionId}`);

  console.log('2. Enqueuing job...');
  // Note: key must be session_id (matching GSON's SerializedName annotation on backend)
  const enqueueRes = await callTool('enqueue_job', {
    body: {
      session_id: sessionId,
      priority: 'NORMAL'
    }
  });
  const jobInfo = JSON.parse(enqueueRes.content[0].text);
  const jobId = jobInfo.body.id;
  console.log(`Job enqueued: ${jobId}`);

  console.log('3. Uploading DSN file...');
  // Note: key must be 'data' (matching the SerializedName("data") annotation on the backend DTO)
  await callTool('upload_job_input_file', {
    path: { jobId: jobId },
    body: {
      data: dsnBase64,
      filename: path.basename(dsnPath),
      format: 'DSN'
    }
  });
  console.log('DSN uploaded successfully.');

  console.log('4. Starting routing job...');
  await callTool('start_job', {
    path: { jobId: jobId }
  });
  console.log('Job started.');

  console.log('5. Monitoring job progress...');
  let completed = false;
  while (!completed) {
    await new Promise(r => setTimeout(r, 2000));
    const statusRes = await callTool('get_job_details', {
      path: { jobId: jobId }
    });
    const statusInfo = JSON.parse(statusRes.content[0].text);
    const jobState = statusInfo.body.state;
    console.log(`Job status: ${jobState}`);
    if (jobState === 'COMPLETED') {
      completed = true;
    } else if (['INVALID', 'TIMED_OUT', 'CANCELLED', 'TERMINATED'].includes(jobState)) {
      throw new Error(`Job ended with state: ${jobState}`);
    }
  }

  console.log('6. Job completed! Downloading output file...');
  const downloadRes = await callTool('download_job_output_file', {
    path: { jobId: jobId }
  });
  
  const downloadInfo = JSON.parse(downloadRes.content[0].text);
  const outputBase64 = downloadInfo.body.data;
  const outputContent = Buffer.from(outputBase64, 'base64');
  fs.writeFileSync(outputPath, outputContent);
  console.log(`Successfully routed and saved SES file to: ${outputPath}`);
}

main().catch(err => {
  console.error('Routing failed:', err);
  process.exit(1);
});
