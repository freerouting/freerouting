#!/usr/bin/env node

const http = require('http');
const https = require('https');
const readline = require('readline');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

const API_URL = process.env.FREEROUTING_API_URL || 'https://api.freerouting.app/v1/mcp';
const httpModule = API_URL.startsWith('https') ? https : http;

function getOrCreateProfileId() {
  const envId = process.env.FREEROUTING_PROFILE_ID || process.env.FREEROUTING__PROFILE__ID;
  if (envId) {
    return envId;
  }
  try {
    const homeDir = os.homedir();
    const configDir = path.join(homeDir, '.freerouting');
    const filePath = path.join(configDir, 'profile_id');
    if (!fs.existsSync(configDir)) {
      fs.mkdirSync(configDir, { recursive: true });
    }
    if (fs.existsSync(filePath)) {
      const id = fs.readFileSync(filePath, 'utf8').trim();
      if (id) return id;
    }
    const newId = crypto.randomUUID ? crypto.randomUUID() : '00000000-0000-0000-0000-000000000000';
    if (newId !== '00000000-0000-0000-0000-000000000000') {
      fs.writeFileSync(filePath, newId, 'utf8');
    }
    return newId;
  } catch (e) {
    return '00000000-0000-0000-0000-000000000000';
  }
}

function sendError(rpcId, code, message) {
  const errResponse = {
    jsonrpc: '2.0',
    id: rpcId,
    error: {
      code: code,
      message: message
    }
  };
  console.log(JSON.stringify(errResponse));
}

function sendToolResponse(rpcId, status, body, isError) {
  const rpcResponse = {
    jsonrpc: '2.0',
    id: rpcId,
    result: {
      content: [{
        type: 'text',
        text: JSON.stringify({
          status: status,
          contentType: 'application/json',
          body: body
        })
      }],
      isError: isError
    }
  };
  console.log(JSON.stringify(rpcResponse));
}

function handleLocalUpload(rpcId, jobId, filePath) {
  if (!jobId || !filePath) {
    sendError(rpcId, -32602, "Missing required parameter: jobId or filePath");
    return;
  }
  
  fs.readFile(filePath, (err, fileBytes) => {
    if (err) {
      sendError(rpcId, -32603, "Failed to read local file: " + err.message);
      return;
    }
    
    const base64Data = fileBytes.toString('base64');
    
    const payload = {
      job_id: jobId,
      data: base64Data
    };
    
    const postData = JSON.stringify(payload);
    
    const headers = {
      'Content-Type': 'application/json',
      'Freerouting-Profile-ID': getOrCreateProfileId()
    };
    const email = process.env.FREEROUTING_PROFILE_EMAIL || process.env.FREEROUTING__PROFILE__EMAIL;
    if (email) {
      headers['Freerouting-Profile-Email'] = email;
    }
    const envHost = process.env.FREEROUTING_ENVIRONMENT_HOST || process.env.FREEROUTING__ENVIRONMENT__HOST;
    if (envHost) {
      headers['Freerouting-Environment-Host'] = envHost;
    }
    if (process.env.FREEROUTING_API_KEY) {
      headers['Authorization'] = 'Bearer ' + process.env.FREEROUTING_API_KEY;
    }
    
    const uploadUrl = API_URL.replace(/\/mcp$/, '') + `/jobs/${jobId}/input`;
    const finalModule = uploadUrl.startsWith('https') ? https : http;
    
    const req = finalModule.request(uploadUrl, {
      method: 'POST',
      headers: headers
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        const isError = res.statusCode >= 400;
        let bodyParsed;
        try {
          bodyParsed = JSON.parse(data);
        } catch (e) {
          bodyParsed = { text: data };
        }
        sendToolResponse(rpcId, res.statusCode, isError ? bodyParsed : { message: "Successfully uploaded input from file: " + filePath }, isError);
      });
    });
    
    req.on('error', (err) => {
      sendError(rpcId, -32603, "Upload request failed: " + err.message);
    });
    
    req.write(postData);
    req.end();
  });
}

function handleLocalDownload(rpcId, jobId, filePath) {
  if (!jobId || !filePath) {
    sendError(rpcId, -32602, "Missing required parameter: jobId or filePath");
    return;
  }
  
  const headers = {
    'Freerouting-Profile-ID': getOrCreateProfileId()
  };
  const email = process.env.FREEROUTING_PROFILE_EMAIL || process.env.FREEROUTING__PROFILE__EMAIL;
  if (email) {
    headers['Freerouting-Profile-Email'] = email;
  }
  const envHost = process.env.FREEROUTING_ENVIRONMENT_HOST || process.env.FREEROUTING__ENVIRONMENT__HOST;
  if (envHost) {
    headers['Freerouting-Environment-Host'] = envHost;
  }
  if (process.env.FREEROUTING_API_KEY) {
    headers['Authorization'] = 'Bearer ' + process.env.FREEROUTING_API_KEY;
  }
  
  const downloadUrl = API_URL.replace(/\/mcp$/, '') + `/jobs/${jobId}/output`;
  const finalModule = downloadUrl.startsWith('https') ? https : http;
  
  const req = finalModule.request(downloadUrl, {
    method: 'GET',
    headers: headers
  }, (res) => {
    let data = '';
    res.on('data', (chunk) => { data += chunk; });
    res.on('end', () => {
      const isError = res.statusCode >= 400;
      if (isError) {
        let bodyParsed;
        try {
          bodyParsed = JSON.parse(data);
        } catch (e) {
          bodyParsed = { text: data };
        }
        sendToolResponse(rpcId, res.statusCode, bodyParsed, true);
        return;
      }
      
      if (res.statusCode === 204) {
        sendToolResponse(rpcId, 204, { message: "Job is in progress but no output data is available yet." }, false);
        return;
      }
      
      try {
        const respObj = JSON.parse(data);
        const base64Data = respObj.data;
        const sesBytes = Buffer.from(base64Data, 'base64');
        
        const dir = path.dirname(filePath);
        if (!fs.existsSync(dir)) {
          fs.mkdirSync(dir, { recursive: true });
        }
        
        fs.writeFile(filePath, sesBytes, (err) => {
          if (err) {
            sendError(rpcId, -32603, "Failed to write downloaded file to disk: " + err.message);
            return;
          }
          sendToolResponse(rpcId, res.statusCode, { message: "Successfully downloaded output and saved to: " + filePath }, false);
        });
      } catch (e) {
        sendError(rpcId, -32603, "Failed to parse download response: " + e.message);
      }
    });
  });
  
  req.on('error', (err) => {
    sendError(rpcId, -32603, "Download request failed: " + err.message);
  });
  req.end();
}

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  if (!line.trim()) return;

  let requestObj = null;
  try {
    requestObj = JSON.parse(line);
  } catch (e) {
    // let it fail or let server handle
  }

  if (requestObj && requestObj.method === 'tools/call' && requestObj.params) {
    const toolName = requestObj.params.name;
    const args = requestObj.params.arguments || {};
    
    if (toolName === 'upload_job_input_from_local_file') {
      handleLocalUpload(requestObj.id, args.jobId, args.filePath);
      return;
    } else if (toolName === 'download_job_output_to_local_file') {
      handleLocalDownload(requestObj.id, args.jobId, args.filePath);
      return;
    }
  }

  const headers = {
    'Content-Type': 'application/json',
    'Freerouting-Profile-ID': getOrCreateProfileId()
  };

  const email = process.env.FREEROUTING_PROFILE_EMAIL || process.env.FREEROUTING__PROFILE__EMAIL;
  if (email) {
    headers['Freerouting-Profile-Email'] = email;
  }

  const envHost = process.env.FREEROUTING_ENVIRONMENT_HOST || process.env.FREEROUTING__ENVIRONMENT__HOST;
  if (envHost) {
    headers['Freerouting-Environment-Host'] = envHost;
  }

  if (process.env.FREEROUTING_API_KEY) {
    headers['Authorization'] = 'Bearer ' + process.env.FREEROUTING_API_KEY;
  }

  const req = httpModule.request(API_URL, {
    method: 'POST',
    headers: headers
  }, (res) => {
    let data = '';
    res.on('data', (chunk) => {
      data += chunk;
    });
    res.on('end', () => {
      const singleLine = data.replace(/\r/g, '').replace(/\n/g, '');
      console.log(singleLine);
    });
  });

  req.on('error', (err) => {
    sendError(requestObj ? requestObj.id : null, -32603, 'Public API request failed: ' + err.message);
  });

  req.write(line);
  req.end();
});
