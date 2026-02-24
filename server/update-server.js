const express = require('express');
const path = require('path');
const fs = require('fs');
const app = express();

const APK_DIR = path.join(__dirname, 'releases');

// Clients waiting for an update (long-poll)
let waitingClients = [];

// GET /api/version — current version info
app.get('/api/version', (req, res) => {
    try {
        const manifest = JSON.parse(
            fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
        res.json(manifest);
    } catch (e) {
        res.status(500).json({ error: 'No release available' });
    }
});

// GET /api/wait-update?v=<currentVersionCode>
// Long-poll: holds connection open until a new version is available
app.get('/api/wait-update', (req, res) => {
    const clientVersion = parseInt(req.query.v) || 0;

    // Check if update already available
    try {
        const manifest = JSON.parse(
            fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
        if (manifest.versionCode > clientVersion) {
            res.json(manifest);
            return;
        }
    } catch (e) {}

    // No update yet — hold connection open
    waitingClients.push({ res, version: clientVersion });
    console.log(`Client v${clientVersion} waiting for update (${waitingClients.length} clients)`);

    // Timeout after 5 minutes — client will reconnect
    req.on('close', () => {
        waitingClients = waitingClients.filter(c => c.res !== res);
        console.log(`Client disconnected (${waitingClients.length} clients)`);
    });

    setTimeout(() => {
        if (!res.writableEnded) {
            res.status(204).end();
            waitingClients = waitingClients.filter(c => c.res !== res);
        }
    }, 5 * 60 * 1000);
});

// POST /api/notify — called by build.sh after successful build
app.post('/api/notify', (req, res) => {
    let manifest;
    try {
        manifest = JSON.parse(
            fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
    } catch (e) {
        res.status(500).json({ error: 'No manifest' });
        return;
    }

    const count = waitingClients.length;
    // Notify all waiting clients
    waitingClients.forEach(client => {
        if (!client.res.writableEnded && manifest.versionCode > client.version) {
            client.res.json(manifest);
        }
    });
    waitingClients = [];

    console.log(`Notified ${count} client(s) of v${manifest.versionCode}`);
    res.json({ notified: count, version: manifest.versionCode });
});

// GET /api/download — download APK
app.get('/api/download', (req, res) => {
    try {
        const manifest = JSON.parse(
            fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
        const apkPath = path.join(APK_DIR, manifest.filename);
        if (fs.existsSync(apkPath)) {
            res.download(apkPath);
        } else {
            res.status(404).json({ error: 'APK not found' });
        }
    } catch (e) {
        res.status(500).json({ error: 'Server error' });
    }
});

const PORT = 7777;
app.listen(PORT, '0.0.0.0', () => {
    console.log(`No Vomito update server on :${PORT}`);
    console.log(`  GET  /api/version       — current version`);
    console.log(`  GET  /api/wait-update?v= — long-poll for update`);
    console.log(`  POST /api/notify        — push to clients`);
    console.log(`  GET  /api/download      — download APK`);
});
