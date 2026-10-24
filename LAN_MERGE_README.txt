Kasse – zusammengeführte LAN-Version (Phase 7)

Inhalt:
- Ausgangspunkt: hochgeladener Kasse-main-Projektstand.
- Der oberste app/-Ordner und die obersten Gradle-Projektdateien wurden durch den LAN-Stand Phase 7 ersetzt.
- GitHub-Workflow, Gradle Wrapper und sonstige Ausgangsdateien bleiben aus dem Ausgangsprojekt erhalten.
- Vorhandene doppelte Unterordner proekt/ und projekt/ wurden nicht als aktive Projektquelle verwendet.

WICHTIG:
- Diese ZIP wurde nicht erfolgreich mit Gradle kompiliert und die LAN-Synchronisierung wurde nicht praktisch getestet.
- Bitte nicht direkt über die bestehende Installation installieren, ohne vorher die Daten zu sichern. Die LAN-Version erweitert das Room-Datenbankschema bis Version 6; Migrationen sind im Code vorgesehen, müssen aber beim Build und auf einem Testgerät überprüft werden.
- Vor dem Einspielen in GitHub den aktuellen Stand sichern oder einen neuen Branch verwenden.
- Die LAN-Kommunikation nutzt derzeit HTTP im lokalen Netz (Cleartext). Das ist keine verschlüsselte Verbindung und sollte nur in einem vertrauenswürdigen WLAN getestet werden.
