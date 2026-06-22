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
