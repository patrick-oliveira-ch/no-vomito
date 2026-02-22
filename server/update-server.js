const express = require('express');
const path = require('path');
const fs = require('fs');
const app = express();

const APK_DIR = path.join(__dirname, 'releases');

app.get('/api/version', (req, res) => {
    try {
        const manifest = JSON.parse(
            fs.readFileSync(path.join(APK_DIR, 'latest.json'), 'utf8'));
        res.json(manifest);
    } catch (e) {
        res.status(500).json({ error: 'No release available' });
    }
});

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
    console.log(`Motion Cues update server running on :${PORT}`);
});
