# Tracker MVP — partage de position Android → carte temps réel

- `server/server.js` : Node sans dépendance. `TOKEN=secret node server.js` puis ouvrir http://localhost:3000 (carte Leaflet, refresh 2 s).
- `android/` : app Kotlin (Activity + service foreground `location`, LocationManager, HTTP POST toutes les ~5 s). APK debug : GitHub Actions `tracker-apk` (artifact), ou `gradle -p tracker/android assembleDebug` avec le SDK Android.

## Contraintes Android à connaître
1. **Service foreground obligatoire** (notification visible) pour continuer écran éteint ; Android 14 exige le type `location` + permission `FOREGROUND_SERVICE_LOCATION`.
2. **Démarrage depuis l'app visible** : Android 12+ interdit de lancer un FGS location depuis l'arrière-plan. Pas de redémarrage auto au boot sans la permission `ACCESS_BACKGROUND_LOCATION`.
3. **Permission en 2 temps** (Android 11+) : fine location d'abord ; `ACCESS_BACKGROUND_LOCATION` à demander séparément (non utilisée ici, inutile tant que le service tourne) et elle impose une revue Play Store.
4. **Économie de batterie** (Xiaomi, Samsung, Huawei…) tue souvent le service : demander d'exclure l'app de l'optimisation batterie.
5. **Doze** coupe le réseau écran éteint longtemps immobile ; le FGS atténue mais ne garantit pas.
6. **Play Store** : déclaration d'usage de la localisation + vidéo de démo + politique de confidentialité.
7. **HTTP en clair** autorisé ici pour tester ; utiliser HTTPS en prod. Émulateur : `10.0.2.2` = machine hôte.
8. Token partagé = sécurité minimale ; prévoir auth par utilisateur/appareil.

## Hébergement (PC éteint)
`render.yaml` à la racine : Render > New > Blueprint > ce repo. Le TOKEN est généré (onglet Environment).
Admin : `https://<service>.onrender.com/?key=<TOKEN>` ; dans l'app : URL du service + TOKEN.
Plan gratuit : le service s'endort après ~15 min sans requête (il se réveille au premier POST, ~30 s) ; l'état est en mémoire (positions perdues au redémarrage, les liens de partage restent valides).
