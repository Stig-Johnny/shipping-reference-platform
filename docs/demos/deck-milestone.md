---
title: "Milepæl: fra lysbilder til kode"
description: "The milestone deck, in Norwegian: what is built and checked, what is still planned, one change the gate rejects, who approves what, and a proposed first 30 days with a new team."
status: accepted
updated: 2026-11-07
kind: deck
lang: nb
milestone: 2026-11-10
mainSlides: 10
appendixSlides: 4
---

# Milepæl: fra lysbilder til kode

## AI-assistert levering, ansvarlige beslutninger

Målet er flere nyttige endringer, med tydelig ansvar for hver av dem.

- Agentene skriver koden og testene
- Påkrevde sjekker må være grønne før sammenslåing
- Et menneske eier beslutningene som ikke kan angres

### Notater

- Dette møtet: det jeg lovet sist, vist i et offentlig repo dere kan åpne selv
- Alle domenedata er syntetiske; fraktappen er et tenkt eksempel
- Hensikten er levering, ikke verktøy for verktøyets skyld

## Løftene, og hva dere kan sjekke i dag

Sjekkeren og avhengighetsporten kjører; fraktappen er planlagt.

| Løfte | I dag |
|---|---|
| Modellen styrer strukturen | Bygget og testet: sjekkeren |
| Porten stopper uvurderte avhengigheter | Deklarerte avhengigheter kontrolleres i det godkjente CI-løpet |
| Selve fraktappen | Planlagt: 27 av 27 funksjoner |

### Notater

- Si det tidlig: applikasjonen er ikke skrevet ennå
- Det som finnes, er kontrollene som skal dømme den
- Dette beviser en del av metoden; levering av produkt er neste bevis jeg skylder

## Én endring porten avviser

Vi fjerner én pil fra sjekkerens egen modell og lar sjekkeren dømme koden.

- Uten pilen: sjekkeren finner to kall i koden som modellen ikke tillater
- Bygget feiler og viser fil og linje
- Pilen tilbake: grønt igjen

Utdrag:

```text
### DIVERGENCE: report -> model
```

Recorded output — not rerun by this site build. Full record in the notes.

### Notater

- Demoen kjøres i en egen kopi av repoet, så ingenting deles ved et uhell
- Først forretningsbetydningen: en avtalt regel om hvem som får bruke hvem
- Så feilmeldingen: hvilke klasser, hvilke kall, hvilken linje
- Går noe galt med nettet: bytt straks til opptaket
- Recorded output — not rerun by this site build. Recorded 2026-11-07T14:56:19Z on revision 44ef312, macOS with Temurin 25.0.5+8: `./gradlew :libs:c4-contract-kit:test --rerun --tests '*SelfConformanceTest'` exited 1 with the relationship `report -> model` removed from `libs/c4-contract-kit/architecture/self.dsl`, and 0 before and after. Excerpt from the checker's report; the report's other headings are left out:

```text
**Verdict: FAILED** with 1 violation. Model: `architecture/self.dsl`. Report format: `c4-contract-kit/report/v1`.
### DIVERGENCE: report -> model
report depends on model, but the model declares no relationship report -> model.
```

## Hva et grønt bygg beviser

Hver påstand trenger riktig type bevis.

- Koden kompilerer, byggets tester består, og bibliotekets registrerte, direkte statiske avhengigheter følger egen modell
- En påkrevd pil bevises av én direkte referanse; et ubrukt felt holder
- At en pris eller booking er riktig, krever egne tester

### Notater

- Sjekkeren ser kompilert kode, ikke hva som skjer når den kjører
- En pil som berører en planlagt modul, vurderes ikke ennå
- Derfor: arkitektursjekk, tester og menneskelig vurdering hver for seg

## Hvem godkjenner hver endring

Alle nivåer krever grønne sjekker og dokumentert AI-godkjenning.

- 0: dokumenttekst. 1: utseende og prototyper uten følsom funksjonalitet, avslått som standard.
- 2: vanlig funksjonalitet.
- 3: følsomme endringer og kontrollene; eieren må også godkjenne nøyaktig versjon manuelt før sammenslåing.

### Notater

- Nivå 3 omfatter penger, booking, persondata, innlogging, skillet mellom nettbutikkenes data, databasestruktur, tredjepartskode og kontrollene
- Automatisk håndheving av AI- og eiergodkjenning er planlagt
- Endringer i SQL-kode, migreringer og behandling av persondata er nivå 3
- Hos dere foreslår jeg at et menneske også sjekker hensikten på nivå 2, slik jeg lovet sist

## En arbeidsflyt teamet deres eier

Sak, tester, endring, gjennomgang, demo: hver gang.

- Brain: et generert sammendrag som agentene skal lese først; bygget feiler hvis det er utdatert
- Vi demonstrerer hver leveranse for dere
- Sjekkene er åpne; README beskriver oppsett og begrensninger

### Notater

- Metoden skal kunne overleveres: tydelig hvem som vedlikeholder sjekker, gjennomganger og innstillinger
- Brain er et sammendrag av kildene, ikke en erstatning for dem

## Sammen med Java-lederen deres

Jeg eier leveransen og de tekniske beslutningene; Java-lederen utfordrer de teknologispesifikke valgene.

- Jeg lærte Java under studiene og har 17 års erfaring med C# og .NET
- Usikkerhet gjør jeg synlig, ikke skjult
- Repoets vedtatte valg er Spring Data JDBC; applikasjonskoden er ikke skrevet. Eventuelle JPA-valg tar vi sammen.

### Notater

- Si Java-bakgrunnen én gang, så videre til ansvar og samarbeid
- Verktøyene i repoet er reelle: strenge bygg, statisk analyse, mutasjonstesting i CI

## Forslag til de første 30 dagene

Vi blir enige om én skive, en avgrenset funksjon fra ende til ende, med akseptkriterier og tilbakerulling.

1. Forstå plattformen, verktøyene og prototypene
2. Porten i deres repo, først bare rapporterende
3. Én skive bak et flagg; dere tar beslutningen om utrulling

### Notater

- Ingen datoer før jeg kjenner plattformen
- Ingen løfter om produksjon før dere har sett skiven virke

## Bevis hver fredag

Dere ser programvare som virker, ikke aktivitet.

- Én side: levert, forsinket, hva porten stoppet, hvilke beslutninger jeg trenger
- En demo av det som kjører
- Vi måler ledetid, omarbeiding og feil som slapp gjennom

### Notater

- Eksempler i det offentlige repoet er syntetiske; deres egne tall holdes internt

## Beslutningen

Bli enige om første skive og hvordan dere vurderer bidraget mitt.

- Første skive og akseptkriteriene
- Tidsbruk og hvem som eier hva
- Når vi ser på resultatet sammen

### Notater

- Spør om prioriteringene deres før detaljene
- Avslutt med en dato for neste vurdering

## Vedlegg: avhengighetsporten

Deklarerte avhengigheter i det godkjente CI-løpet sjekkes mot registrerte vurderinger før bygget starter.

- Sjekken kjøres fra hovedgrenen mot endringens filer, som data
- 308 vurderte oppføringer: 293 godkjente, 15 avviste
- En vurdering kan ta feil; betingelser håndheves ikke overalt

### Notater

- Detaljene står på Trust-sidene i nettstedet

## Vedlegg: bevis på at portene feiler

To endringer ble laget for å feile, og ble aldri slått sammen.

- Pull request #16: en uvurdert avhengighet; sjekken avsluttet med kode 1
- Pull request #17: en liste som ikke kunne leses; sjekken avsluttet med kode 2
- Manglende `pipefail` skjulte feilstatusen fra avhengighetssjekken; dette ble rettet i #15

### Notater

- Hele historikken står i security/README.md

## Vedlegg: hvordan en agent fortsatt kan svekke kontrollene

Dette sier jeg før dere spør.

- Utvide modellen, eller svekke tester og arbeidsflyter i sin egen endring
- Regne ut kontrollsummen for det vurderte settet på nytt
- Avhengighetssjekken kjøres fra hovedgrenen; nivå 3 krever manuell eiergodkjenning, som ennå ikke håndheves automatisk

### Notater

- Håndhevet godkjenning og en egen AI-gjennomgangssjekk er planlagt
- Kontrollene er ikke en sertifisering

## Vedlegg: verktøy og versjoner

Java 25, strenge bygg og statisk analyse.

- Temurin 25.0.5+8, Gradle med låste og verifiserte avhengigheter
- Error Prone og NullAway; sjekkeren bruker ArchUnit og Structurizr DSL
- Mutasjonstesting av sjekkeren i CI: terskel 90 %, uten krav om grønt resultat før sammenslåing

### Notater

- Spring Boot-avhengighetene er deklarert; kjørende app og database er planlagt
