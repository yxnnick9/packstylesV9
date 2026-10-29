# yxnnick9 Texture Studio (Fabric, Minecraft 1.21.11)

Client-only Mod: Waehle, aus welchem deiner Texturepacks (Ordner `resourcepacks`) welche Teile uebernommen werden.

## Tabs
- **Categories** (End Crystal mit 3D-Vorschau + eingebauten Styles wie Aqua/Blueberry/Lavender; Mace/3D-Packs mit 3D-Vorschau): HUD (Herzen, Hunger, XP, Hotbar, Inventar), Waffen (Sword, Tools, Spear, Mace, Trident, Bow, Arrow, Crossbow, Shield),
  Ruestung, Items, Bloecke, Glint, Crit-Partikel, Mining Cracks, Sounds (mit Lautstaerke) und Totem-Groesse in der Hand.
  Pro Kategorie ein Pack waehlen -> **Apply**. 3D-Modelle des Packs (items/models) werden mitkopiert.
- **Items**: einzelne Item-/Block-Texturen aus beliebigen Packs.
- **Draw**: eigene Pixel-Art-Texturen (16x16 / 32x32) fuer ein Item zeichnen und im Pack speichern.
- **Sky**: 24 generierte 4K-Panoramen (Stars, Nebula, Aurora, Sunset, Minimal) mit **eigenem Renderer** - keine Zusatz-Mod noetig.
  Eigene Bilder (z.B. Anime-Figuren) freistellen, im Panorama platzieren und als Kombi speichern.
  Nach dem Auswaehlen **Apply** druecken. Bei Problemen faellt die Mod auf den normalen Himmel zurueck (Hinweis im Log).
- **Design**: Colorways, Farbfeld, Deckkraft, Abdunkeln, Groesse, Hintergrundbild, Abbau-Farbe.
- Unten rechts: Sprache Deutsch / English.

## Bedienung
- **Rechte Shift-Taste** oeffnet das Studio (aenderbar unter Optionen > Steuerung).
- Vollbild, mit - / + (oder Strg + Mausrad) kleiner/groesser; die Groesse bleibt gespeichert. Unter 100% Fenster am Header in die Ecken ziehen.

## Sicherheit
- Liest nur den `resourcepacks`-Ordner (Packs werden nicht veraendert).
- Schreibt nur `resourcepacks/yxnnick9_texturestudio` und `config/yxnnick9-texturestudio/`.
- Einzige Netzwerkanfrage: einmaliger Download des Kopf-Avatars (mc-heads.net), abschaltbar mit `"fetchHead": false`.
- Kein Nachladen von Code, kein Zugriff auf Account/Session.

## Bauen
GitHub: Repo erstellen, alle Dateien hochladen (+ `.github/workflows/build.yml` ggf. ueber "Create new file" anlegen),
unter **Actions** laeuft der Build, die JAR liegt unter **Artifacts**.
Lokal: `gradle build` (Gradle 9.2.1, Java 21).

## Credits
Die Technik des Himmel-Renderers (Panorama-Kugel statt Sterne, Einhaenge-Punkte, Render-Pass) wurde anhand der MIT-lizenzierten
Mod "Customization Studio" von Marv nachgebaut - siehe `NOTICE`.
