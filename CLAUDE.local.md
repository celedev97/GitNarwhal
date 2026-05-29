# Regole generali

**Non firmare MAI come co-autore nei commit.** Non aggiungere righe
`Co-Authored-By: Claude ...` né in commit messages né altrove.

---

# Local workflow

Per ogni task su questo repo, a meno che non venga specificato diversamente:

1. Fai il task
2. Commit
3. `git pull`
4. Tag con bump `+0.0.1` rispetto all'ultimo tag (es. `v1.0.26` → `v1.0.27`)
5. `git push` (commit + tag)

## Coverage

La soglia minima è **80% instruction coverage** sul codice `backend` e `utils`
(views/components/UI sono escluse dalla metrica — coperte dai smoke test headless).

**Come verificare:**
```bash
./gradlew test                          # esegue test + genera report JaCoCo
./gradlew jacocoTestCoverageVerification  # fallisce se < 80%
./gradlew check                         # esegue entrambi + verifica soglia
```

Report HTML: `build/reports/jacoco/test/html/index.html`

**Quando aggiungi nuove feature:**
- Se aggiungi logica in `backend/` o `utils/`, scrivi unit test per essa.
- Esegui `./gradlew check` prima di fare il commit per verificare che la soglia sia ancora rispettata.
