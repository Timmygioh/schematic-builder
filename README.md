# Schematic Builder (Fabric, Minecraft 26.2)

Pastet Sponge-Schematics (.schem, v2/v3) per Befehl, verteilt ueber mehrere Ticks.

## Benutzung
1. .schem-Datei in `<spielordner>/schematics/` legen (bei Singleplayer: .minecraft/schematics).
2. Im Spiel (Cheats an / OP):
   - `/schem list`
   - `/schem load <name>`
   - `/schem paste [ignoreAir]`  (Ecke der Schematic = deine Position)
   - `/schem undo`, `/schem cancel`

## Bauen
Java 25 + Gradle 9.5.1 noetig.
    gradle wrapper --gradle-version 9.5.1
    ./gradlew build
Die Jar liegt danach in build/libs/.
