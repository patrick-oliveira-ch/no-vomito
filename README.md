# No Vomito

**Application Android anti mal des transports** - Overlay de points visuels qui défilent en synchronisation avec le mouvement réel, pour réduire le mal des transports (cinétose).

## Pourquoi ?

Apple a introduit les **Vehicle Motion Cues** dans iOS 18 : des points animés en périphérie de l'écran qui bougent en phase avec le véhicule. C'est simple, élégant, et ça marche vraiment bien pour réduire la nausée en voiture.

Sur Android, **Kinestop** existe mais ne donne pas les mêmes résultats :
- Les animations ne suivent pas fidèlement le mouvement réel du véhicule
- Pas de prise en compte de l'orientation du téléphone
- Réactivité insuffisante dans les virages

**No Vomito** reproduit le comportement d'Apple avec une approche plus poussée :
- **Fusion de capteurs** : rotation vector + accéléromètre + GPS pour une détection précise du mouvement
- **Indépendant de l'orientation du téléphone** : que le téléphone soit face à la route, de côté, ou à plat — les points vont toujours dans la bonne direction
- **Proportionnel à la vitesse GPS** : défilement doux en marche, fluide en ville, rapide sur autoroute
- **Virages amplifiés par la vitesse** : virage à 80 km/h = gros effet latéral, virage à pied = subtil

## Fonctionnalités

- Overlay transparent par-dessus toutes les applications
- Défilement des points proportionnel à la vitesse GPS (marche, vélo, voiture)
- Détection des virages via gyroscope + accéléromètre
- Fonctionne quelle que soit l'orientation du téléphone (portrait, paysage, incliné)
- Activation automatique au-dessus d'une vitesse configurable

### Paramètres ajustables

| Paramètre | Description |
|-----------|-------------|
| **Couleur** | Noir, gris, blanc, bleu, vert, rouge, orange |
| **Opacité** | Transparence des points (0-100%) |
| **Sensibilité** | Réactivité aux accélérations et virages |
| **Vitesse de défilement** | Ratio vitesse GPS / défilement des points |
| **Nombre de points** | Densité de la grille (18 à 128 points) |
| **Vitesse minimum** | Seuil d'activation en km/h (0 = toujours actif) |

## Installation

1. Télécharger le fichier APK depuis les [Releases](../../releases)
2. Autoriser l'installation depuis des sources inconnues
3. Installer l'APK
4. Accorder les permissions : overlay, localisation, notifications

## Permissions requises

- **Affichage par-dessus les autres apps** : pour l'overlay de points
- **Localisation GPS** : pour la vitesse et la direction de déplacement
- **Notifications** : pour le service en arrière-plan

## Comment ça marche

```
GPS (vitesse + cap)
        │
        ▼
┌─────────────────┐     ┌──────────────────┐
│ Rotation Vector │────▶│   MotionEngine   │────▶ motionX, motionY
│ (orientation    │     │                  │
│  du téléphone)  │     │ Accel monde      │
│                 │     │ → Accel véhicule │
│ Accéléromètre   │────▶│                  │
│ (forces G)      │     │ Speed scroll     │
└─────────────────┘     │ projeté sur écran│
                        └──────────────────┘
                                │
                                ▼
                        ┌──────────────────┐
                        │ DotsOverlayView  │
                        │                  │
                        │ Grille de points │
                        │ avec wrapping    │
                        │ + fade aux bords │
                        └──────────────────┘
```

1. Le **capteur de rotation** donne l'orientation absolue du téléphone dans l'espace
2. L'**accéléromètre** est transformé du repère téléphone → repère monde (Nord/Est) → repère véhicule (Avant/Droite) grâce au cap GPS
3. La **vitesse GPS** génère un défilement continu projeté sur les axes de l'écran
4. Les points **bouclent** horizontalement et verticalement avec un fondu aux bords

## Specs techniques

- Android 10+ (API 29)
- ~32 KB (APK ultra-léger, pas de dépendances externes)
- Compilé sans Gradle (aapt2 + javac + d8 natifs ARM64)
- Rafraîchissement 60 FPS
- Capteurs : LINEAR_ACCELERATION, ROTATION_VECTOR, GPS

## Mise à jour OTA

L'app inclut un système de mise à jour automatique via un serveur local (long-poll). Bouton "Vérifier mise à jour" dans les paramètres.

## Licence

MIT

## Auteur

**Patapps** - [@patrick-oliveira-ch](https://github.com/patrick-oliveira-ch)
