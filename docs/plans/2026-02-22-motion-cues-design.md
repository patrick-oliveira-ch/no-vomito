# Motion Cues - Android Anti Motion Sickness App

## Overview
Clone Android de la fonctionnalité "Vehicle Motion Cues" d'Apple iOS 18.
Affiche des points animés sur les bords de l'écran qui suivent le mouvement du véhicule pour réduire le mal des transports.

## Architecture

### Stack technique
- **Frontend** : HTML5 Canvas + CSS animations (points animés)
- **Runtime natif** : Capacitor (package web app en APK)
- **Overlay** : Plugin natif Android custom (WindowManager TYPE_APPLICATION_OVERLAY)
- **Capteurs** : Accéléromètre + Gyroscope + GPS via APIs natives
- **OTA** : L'app télécharge les assets JS mis à jour depuis le serveur Pi
- **Build** : Android SDK CLI sur Raspberry Pi (pas besoin d'Android Studio)

### Fonctionnalités
1. **Points animés sur les bords** : ~8-12 points répartis sur les bords gauche/droit de l'écran
2. **Réponse au mouvement** : Les points se déplacent horizontalement (virage) et verticalement (accélération/freinage)
3. **Détection auto véhicule** : GPS détecte si vitesse > 10 km/h = en véhicule
4. **Overlay permanent** : Fonctionne par-dessus toutes les apps
5. **Choix couleurs** : 6 couleurs + mode grayscale
6. **Notification persistante** : Contrôle start/stop depuis la barre de notif
7. **OTA updates** : Vérification auto des MAJ depuis le serveur Pi

### Composants

```
motion-cues/
├── src/                    # Web app (Canvas + JS)
│   ├── index.html
│   ├── motion-engine.js    # Logique capteurs + calcul mouvement
│   ├── dots-renderer.js    # Rendu Canvas des points
│   ├── settings.js         # Préférences (couleur, sensibilité)
│   └── updater.js          # OTA update checker
├── android/                # Projet Android (Capacitor)
│   └── app/src/main/java/
│       └── OverlayService.java  # Service overlay Android
├── capacitor.config.ts
├── package.json
└── server/                 # Serveur MAJ sur le Pi
    └── update-server.js    # Sert les assets + version check
```

### Data flow
1. Accéléromètre/Gyroscope → motion-engine.js (filtre passe-bas, lissage)
2. GPS → détection véhicule (vitesse > 10 km/h)
3. motion-engine → dots-renderer (position des points calculée)
4. dots-renderer → Canvas overlay (rendu 60fps)

### OTA Update
- Le serveur Pi expose `/api/version` et `/api/assets.zip`
- L'app vérifie au lancement si une nouvelle version existe
- Si oui, télécharge le zip, extrait dans le répertoire local, relance le WebView
- Pas besoin de réinstaller l'APK

## Contraintes
- Build sur Raspberry Pi ARM64 (pas d'Android Studio)
- Android SDK CLI tools uniquement
- Cible : Android 10+ (API 29+)
- Permission : SYSTEM_ALERT_WINDOW pour l'overlay
