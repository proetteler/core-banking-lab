# Core Banking Lab – Regeln für Claude Code

## Worum es geht
Lernprojekt: Grundmechanik eines Kernbankensystems (Oracle, PL/SQL, später REST, Kafka, Kubernetes).
Ziel ist Verstehen für einen Einstieg als Avaloq-Entwickler oder Avaloq Integration Engineer, nicht ein fertiges Produkt.
Patrick muss jede Zeile und jede Entscheidung im Vorstellungsgespräch erklären können.

## Arbeitsweise (verbindlich)
- Zuerst erklären: was, warum, was es braucht. Erst nach Patricks ausdrücklichem OK umsetzen.
- Immer nur ein Baustein auf einmal. Keine grossen Schritte auf Vorrat.
- Keine Dateien anlegen, ändern oder löschen ohne ausdrückliche Bestätigung. Patrick legt Dateien in der Regel selbst an.
- PL/SQL-Banklogik schreibt Patrick selbst. Claude gibt Hinweise, erklärt Konzepte und macht Reviews, schreibt aber keine Lösung, ausser Patrick fragt ausdrücklich danach.
- Einfach erklären, mit Bezug zu Jira, Confluence und Applikationsbetrieb.
- Ehrlich sein: Fehler und Missverständnisse direkt ansprechen.
- Bei wichtigen Entscheidungen auf einen ADR hinweisen (arc42 / CPSA-F).
- Sprache: Deutsch, Schweizer Schreibweise (ss statt ß).

## Pfade
- Code (dieses Repo): /Users/patrick/dev/core-banking-lab
- Lernnotizen und grobe Projektdoku (Obsidian, nicht im Repo):
  /Users/patrick/Documents/Obsidian/SecondBrain/Projects/core-banking-lab
  Struktur: README.md, steps/, concepts/, glossary.md, architecture/ (adr/), interview/
- Kein Code im Vault, keine Lernnotizen im Repo.

## Technik
- Alles in Containern über Docker Desktop und Docker Compose (Oracle Database Free, später Kafka, Kafka UI, Integrationsservice).
- Mac M4 (ARM64): nur Images verwenden, die arm64 unterstützen.
- Datenbankdaten in einem Docker-Volume, nicht im Repo.
- Zugangsdaten nur in .env (nie committen). Nie echte Passwörter in Code, Doku oder Commits.
- GitHub: github.com/proetteler, zuerst privat.

## Ehrlichkeit
- Kein Avaloq-Code, keine Avaloq-Interna. Nur öffentlich bekannte Konzepte nachbilden.
