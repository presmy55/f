# Relais

Relais transforme un téléphone Android en clavier et souris pour un ordinateur.
Le mode Bluetooth utilise le profil HID Android. Le mode Wi-Fi utilise le petit
programme de bureau fourni dans `desktop/`.

## Mode Wi-Fi

Le téléphone et l’ordinateur doivent être connectés au même réseau Wi-Fi privé.
N’utilisez pas ce programme sur un réseau public et ne configurez pas de
redirection du port 5005 sur votre routeur.

1. Sur l’ordinateur, installez Python 3.10 ou plus récent.
2. Installez les dépendances avec `python -m pip install -r desktop/requirements.txt`.
3. Lancez `python desktop/relais_pc.py`.
4. L’application PC affiche son adresse IP et un code. Saisissez-les dans le
	téléphone, puis choisissez « Mode : Wi-Fi ».
5. Touchez « Démarrer » dans l’application PC. Le pavé tactile et les touches du
	téléphone envoient alors leurs commandes à cet ordinateur.

Sous Windows, utilisez `py` au lieu de `python` si cette commande est configurée
sur votre ordinateur. Sous ChromeOS, ouvrez d’abord l’environnement Linux.
Sur Chromebook, privilégiez le mode Bluetooth : l’environnement Linux ne peut
généralement pas contrôler le bureau ChromeOS. Le mode Wi-Fi est prévu pour un
ordinateur avec une session de bureau compatible avec `pynput`.

Le pare-feu de l’ordinateur peut demander l’autorisation pour Python. Autorisez
la communication uniquement sur les réseaux privés. Le mode Wi-Fi utilise un
code partagé sur le réseau local; il ne chiffre pas les paquets. Arrêtez le
serveur PC quand vous avez terminé.

## Android

Ouvrez le projet dans Android Studio et lancez la tâche `assembleDebug`, ou
utilisez l’APK `app-debug.apk` produit par GitHub Actions.

En Bluetooth, l’écran « Manette » envoie un contrôleur HID générique avec deux
joysticks, une croix directionnelle, A/B/X/Y, LB/RB, LT/RT, L3/R3, Select et
Start. La prise en charge dépend du téléphone Android et de l’ordinateur ; les
jeux peuvent interpréter les boutons différemment d’une manette Xbox. Les jeux
qui exigent exclusivement XInput peuvent ne pas accepter ce contrôleur HID
générique ; cette compatibilité ne peut pas être forcée depuis Android.

En Wi-Fi, le programme PC contrôle le clavier et la souris : pavé tactile,
clics gauche/milieu/droit, molette, Z/Q/S/D et espace. Il ne crée pas de
manette virtuelle. Pour utiliser la manette sur Chromebook, choisissez
Bluetooth et associez le téléphone dans les paramètres Bluetooth de ChromeOS.