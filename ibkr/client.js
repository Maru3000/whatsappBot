// Client for IBKR's Client Portal Web API (https://localhost:5000/v1/api by default).
// Requires the Client Portal Gateway to be running locally and an active browser SSO login.
const https = require('https');
const axios = require('axios');

const BASE_URL = process.env.IBKR_GATEWAY_URL || 'https://localhost:5000/v1/api';

// The Client Portal Gateway serves a self-signed cert on localhost by default.
const httpsAgent = new https.Agent({ rejectUnauthorized: false });

const client = axios.create({
  baseURL: BASE_URL,
  httpsAgent,
  timeout: 10000,
});

async function getAuthStatus() {
  const { data } = await client.post('/iserver/auth/status');
  return data;
}

async function getAccounts() {
  const { data } = await client.get('/portfolio/accounts');
  return data;
}

async function getAccountSummary(accountId) {
  const { data } = await client.get(`/portfolio/${accountId}/summary`);
  return data;
}

module.exports = { getAuthStatus, getAccounts, getAccountSummary, BASE_URL };
