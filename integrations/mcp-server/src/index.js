#!/usr/bin/env node

const http = require('http');
const https = require('https');
const readline = require('readline');

const API_URL = process.env.FREEROUTING_API_URL || 'https://api.freerouting.app/v1/mcp';
const httpModule = API_URL.startsWith('https') ? https : http;

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  if (!line.trim()) return;

  let parsedId = null;
  try {
    const parsed = JSON.parse(line);
    parsedId = parsed.id !== undefined ? parsed.id : null;
  } catch (e) {
    // Silent ignore parsing error on input check; let server reject or handle
  }

  const headers = {
    'Content-Type': 'application/json',
    'Freerouting-Profile-ID': process.env.FREEROUTING_PROFILE_ID || '00000000-0000-0000-0000-000000000000',
    'Freerouting-Profile-Email': process.env.FREEROUTING_PROFILE_EMAIL || 'mcp-npx-client@local.freerouting.app',
    'Freerouting-Environment-Host': process.env.FREEROUTING_ENVIRONMENT_HOST || 'MCP-NPX-Client/1.0'
  };

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
    const errResponse = {
      jsonrpc: '2.0',
      id: parsedId,
      error: {
        code: -32603,
        message: 'Public API request failed: ' + err.message
      }
    };
    console.log(JSON.stringify(errResponse));
  });

  req.write(line);
  req.end();
});
