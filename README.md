# itur-java

Implémentation Java (cible Java 25, sans dépendance d'exécution) de modèles de propagation UIT-R, portée depuis [ITU-Rpy 0.4.0](https://github.com/inigodelportillo/ITU-Rpy) et validée contre celui-ci.

## Recommandations couvertes

| Rec. | Objet | Classe |
|---|---|---|
| P.618-13 | Affaiblissement pluie, scintillation, XPD, diversité de site | `P618` |
| P.676-13 | Affaiblissement gazeux (raie par raie, hauteurs équivalentes Annexe 2, trajet exact par tracé de rayon) | `P676` |
| P.837-7 | Intensité de pluie | `P837` |
| P.838-3 | Affaiblissement linéique de la pluie | `P838` |
| P.839-4 | Hauteur de pluie / isotherme 0 °C | `P839` |
| P.840-7 | Nuages (contenu en eau liquide, coefficient, affaiblissement) | `P840` |
| P.453-13 | Indice de réfraction radio | `P453` |
| P.836-6 | Vapeur d'eau (densité, contenu total) | `P836` |
| P.1510-1 | Température moyenne de surface | `P1510` |
| P.1511-1 | Altitude topographique (bicubique) | `P1511` |
| (P.835) | Atmosphère standard (dépendance de P.676/P.836) | `P835` |

`Itur.atmosphericAttenuationSlantPath(...)` compose gaz + nuages + pluie + scintillation (`A = Ag + sqrt((Ar+Ac)² + As²)`).

> **Versions** : ITU-Rpy a déjà des versions plus récentes pour P.618 (-14), P.840 (-9) et P.1511 (-2). Les versions demandées ont été implémentées telles quelles ; la mise à niveau est possible (les données sont disponibles).

## Démarrage rapide

```java
import org.itur.*;

var a = Itur.atmosphericAttenuationSlantPath(48.85, 2.35, 20.0, 35.0, 0.1, 1.2, Itur.Options.defaults());
System.out.println(a.total() + " dB");
```

Exemple exécutable : `java -cp target/classes examples/LinkBudgetExample.java`
(Paris, 20 GHz, 35°, p = 0,1 %, 1,2 m : gaz 0,95 ; nuages 0,78 ; pluie 4,14 ; scintillation 0,45 ; total 5,89 dB).

Conventions : latitude/longitude en degrés, fréquence en GHz, hauteurs en km, probabilités `p` en %, API scalaire, `Double` nullable = valeur optionnelle (calculée depuis les cartes si `null`).

## Données

Les cartes ITU sont converties de `.npz` en `.grid` (gzip, float32, big-endian) dans `src/main/resources/itur/data` (~80 Mo).
- `tools/convert_data.py` : régénère les `.grid` depuis ITU-Rpy.
- `tools/generate_golden.py` : régénère les vecteurs de référence (`src/test/resources/golden/*.csv`) depuis ITU-Rpy.

Les données ITU sont soumises aux conditions d'utilisation de l'UIT.

## Validation

~11 000 vecteurs (villes, points aléatoires, points limites : pôles, ±180°, 360°) comparés à ITU-Rpy, tolérance relative 1e-9 :

- Tout passe à ≤ 1e-11, sauf P.618 quantiles (≤ 2,4e-7) : tolérance documentée de 2e-5.
- P.676-13 est aussi validée contre les tableaux officiels de l'UIT (`src/test/resources/official/676`, test `P676OfficialTablesTest`) : γ0/γw/γ (350 lignes), A_gas Annexe 2 (10 lignes) et la feuille A1 (28 GHz, 30°), à moins de 5e-6.
- Les données sont arrondies en float32 des deux côtés pour comparer les mêmes entrées ; `ReferenceValuesTest` vérifie en float64 à 1e-5.

## Écarts connus (reproduits d'ITU-Rpy)

- `P0` de P.618 en %.
- Terme humide de P.453 : température en °C.
- `NaN` aux limites de grille (ex. lat = -90° pour P.836/P.453), comme ITU-Rpy.
- P.840 : erreur pour p > 99 %; P.840-7 sans affaiblissement lognormal.
- P.835 : atmosphère standard de référence uniquement.
- P.676-13 : pas d'approximation explicite de γ0/γw ; `gamma0Approx`/`gammaWApprox` (dépréciées) renvoient le γ total raie par raie, comme ITU-Rpy ; les modes `APPROX`/`EXACT` des trajets terrestres sont donc identiques.

## Build

```
mvn test
```

Non compilé avec Maven/Java 25 dans mon environnement (réseau bloqué) : validé avec javac 21, un jar assemblé à la main et un mini-lanceur JUnit. Un premier `mvn test` chez vous est la vérification finale.

## Licence

MIT, avec la mention de copyright d'ITU-Rpy (voir `LICENSE`).
