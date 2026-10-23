# Règles R8 de l'app (build release). Les bibliothèques embarquent leurs propres règles (consumer rules) :
# n'ajouter ici que ce qui manque réellement, avec la raison.

# Garder les numéros de ligne dans les traces de plantage (fichier de mapping pour les retrouver).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor et kotlinx.coroutines référencent des classes JVM absentes d'Android (journalisation, outils de debug) :
# jamais appelées sur Android, R8 ne doit pas échouer dessus.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn reactor.blockhound.**
