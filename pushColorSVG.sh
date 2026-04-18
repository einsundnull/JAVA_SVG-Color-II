#!/bin/bash
# === SICHERES GIT DEPLOYMENT SCRIPT ===
# Für PosterAppMultiColorSVG (Eclipse Projekt)

# === 1. Konfiguration ===
PROJEKT_PFAD="C:\Users\pc\eclipse-workspace\SVG_Color\src\main\java\main"
GITHUB_URL="https://github.com/einsundnull/JAVA_SVG-Color-II.git"

echo "🚀 Starte Git Deployment für PosterAppMultiColorSVG..."

# === 2. Verzeichnis prüfen ===
cd "$PROJEKT_PFAD" || {
  echo "❌ Pfad existiert nicht: $PROJEKT_PFAD"
  read -p "Drücke Enter zum Beenden..."
  exit 1
}
echo "✅ Arbeitsverzeichnis: $(pwd)"

# === 3. Git Initialisierung ===
if [ ! -d ".git" ]; then
  echo "🔧 Initialisiere Git Repository..."
  git init
  echo "✅ Git Repository initialisiert."
else
  echo "✅ Git Repository existiert bereits."
fi

# === 4. Git Status anzeigen ===
echo "📋 Git Status:"
git status

# === 5. Änderungen prüfen ===
if git diff --quiet && git diff --staged --quiet; then
  echo "⚠️ Keine Änderungen gefunden."
  read -p "Trotzdem fortfahren? (y/n): " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "❌ Deployment abgebrochen."
    read -p "Drücke Enter zum Beenden..."
    exit 0
  fi
fi

# === 6. Dateien hinzufügen ===
echo "📦 Füge alle Änderungen hinzu..."
git add -A

if git diff --staged --quiet; then
  echo "⚠️ Keine staged Änderungen gefunden."
else
  echo "✅ Änderungen gestaged."
fi

# === 7. Commit erstellen ===
COMMIT_MSG="Deployment Update - $(date '+%Y-%m-%d %H:%M:%S')"
echo "💾 Erstelle Commit: $COMMIT_MSG"

if git commit -m "$COMMIT_MSG"; then
  echo "✅ Commit erfolgreich."
else
  echo "⚠️ Kein Commit notwendig."
fi

# === 8. Branch setzen ===
git branch -M main

# === 9. Remote setzen ===
git remote remove origin 2>/dev/null
git remote add origin "$GITHUB_URL"
echo "🔗 Remote origin: $GITHUB_URL"

# === 10. Push ===
echo "🚀 Push nach GitHub..."
if git push origin main; then
  echo "✅ Push erfolgreich!"
else
  echo "⚠️ Push fehlgeschlagen."
  read -p "Mit --force-with-lease pushen? (y/n): " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    git push --force-with-lease origin main && echo "✅ Force Push erfolgreich." && read -p "Drücke Enter zum Beenden..." && exit 0
  fi
  echo "❌ Push abgebrochen."
  read -p "Drücke Enter zum Beenden..."
  exit 1
fi

# === 11. Fertig ===
echo "🎉 Fertig! Repo: $GITHUB_URL"
read -p "Drücke Enter zum Beenden..."
