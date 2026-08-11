// Import Express.js
const express = require('express');
const { getAuthStatus, getAccounts, getAccountSummary } = require('./ibkr/client');
const { assessAccountStability } = require('./ibkr/stability');
const { sendWhatsAppMessage } = require('./whatsapp/client');

// Create an Express app
const app = express();

// Middleware to parse JSON bodies
app.use(express.json());

// Set port and verify_token
const port = process.env.PORT || 3000;
const verifyToken = process.env.VERIFY_TOKEN;

// Route for GET requests
app.get('/', (req, res) => {
  const { 'hub.mode': mode, 'hub.challenge': challenge, 'hub.verify_token': token } = req.query;

  if (mode === 'subscribe' && token === verifyToken) {
    console.log('WEBHOOK VERIFIED');
    res.status(200).send(challenge);
  } else {
    res.status(403).end();
  }
});

async function buildStabilityReport(accountId) {
  const authStatus = await getAuthStatus();
  if (!authStatus.authenticated || !authStatus.connected) {
    return {
      connected: false,
      message: 'IBKR Client Portal Gateway session is not authenticated. Log in at https://localhost:5000 and try again.',
    };
  }

  const accounts = await getAccounts();
  const targetId = accountId || accounts?.[0]?.accountId || accounts?.[0]?.id;
  if (!targetId) {
    return { connected: true, message: 'No IBKR accounts found for this session.' };
  }

  const summary = await getAccountSummary(targetId);
  const { rating, metrics, reasons } = assessAccountStability(summary);

  return { connected: true, accountId: targetId, rating, metrics, reasons };
}

function formatStabilityMessage(report) {
  if (!report.connected) return report.message;
  if (!report.rating || report.rating === 'UNKNOWN') {
    return report.message || `Connected to account ${report.accountId}, but could not compute a stability rating from the returned data.`;
  }

  const lines = [
    `IBKR account ${report.accountId} stability: ${report.rating}`,
    report.metrics.netLiquidation != null ? `Net liquidation: ${report.metrics.netLiquidation}` : null,
    report.metrics.marginCushionPct != null ? `Margin cushion: ${report.metrics.marginCushionPct.toFixed(1)}%` : null,
    report.metrics.maintMarginUsagePct != null ? `Maintenance margin usage: ${report.metrics.maintMarginUsagePct.toFixed(1)}%` : null,
    ...report.reasons,
  ].filter(Boolean);

  return lines.join('\n');
}

// On-demand stability check, e.g. for polling from a browser or curl.
app.get('/ibkr/stability', async (req, res) => {
  try {
    const report = await buildStabilityReport(req.query.accountId);
    res.status(200).json(report);
  } catch (err) {
    console.error('IBKR stability check failed:', err.response ? err.response.data : err.message);
    res.status(502).json({
      connected: false,
      error: 'Could not reach the IBKR Client Portal Gateway. Make sure it is running and you are logged in.',
    });
  }
});

// Route for POST requests
app.post('/', async (req, res) => {
  const timestamp = new Date().toISOString().replace('T', ' ').slice(0, 19);
  console.log(`\n\nWebhook received ${timestamp}\n`);
  console.log(JSON.stringify(req.body, null, 2));

  const phone = req.body.from;
  const messageText = req.body.message;

  if (phone && /\b(ibkr|stability)\b/i.test(messageText || '')) {
    try {
      const report = await buildStabilityReport();
      await sendWhatsAppMessage(phone, formatStabilityMessage(report));
    } catch (err) {
      console.error('Failed to send IBKR stability reply:', err.response ? err.response.data : err.message);
    }
  }

  res.status(200).end();
});

// Start the server
app.listen(port, () => {
  console.log(`\nListening on port ${port}\n`);
});
