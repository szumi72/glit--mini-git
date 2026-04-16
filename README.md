# glit--mini-git

Te pliki "a" w tych pakietach to tylko dlatego żeby sie dodał folder do repo.

# Struktura projektu – Glit
## 📁 Struktura pakietów

```
glit
├── cli        # obsługa wejścia użytkownika (CLI)
├── model      # modele danych (Blob, Tree, Commit)
├── service    # logika aplikacji (operacje repozytorium)
├── storage    # zapis/odczyt danych (.glit)
├── merge      # logika mergowania
└── util       # klasy pomocnicze
```

---

## 📁 glit.model

Zawiera klasy reprezentujące dane systemu (bez logiki biznesowej).

* `Blob` – zawartość pliku + hash
* `Tree` – struktura katalogów i plików
* `TreeEntry` – wpis w drzewie (nazwa, hash, typ)
* `Commit` – commit (parent, author, timestamp, message, treeHash)

---

## 📁 glit.storage

Odpowiada za zapis i odczyt danych z katalogu `.glit/`.

* `ObjectDatabase`

  * zapis i odczyt obiektów (Blob, Tree, Commit)
* `Index`

  * staging area (pliki przygotowane do commita)
* `RefManager`

  * zarządzanie HEAD i branchami

---

## 📁 glit.service

Zawiera główną logikę aplikacji.

* `Repository`

  * implementacja komend:

    * init
    * add
    * commit
    * checkout
    * merge

* `TreeBuilder`

  * buduje strukturę Tree na podstawie Index

---

## 📁 glit.merge

Zawiera logikę łączenia gałęzi.

* `MergeEngine`
---

## 📁 glit.cli

Obsługa interfejsu tekstowego (CLI).

* `GlitController`

  * parsowanie komend użytkownika
  * wywoływanie metod Repository

* `Main`

  * punkt wejścia aplikacji

---

## 📁 glit.util

Klasy pomocnicze.

* `HashUtils` – obliczanie SHA-1
* `CompressUtils` – kompresja danych


---

## 🔗 Zależności między warstwami

```
CLI → Service → Storage → Model
            ↘ Merge
```

* CLI tylko wywołuje logikę
* Service zarządza operacjami
* Storage zajmuje się plikami
* Model przechowuje dane


