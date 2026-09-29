# Trial_1 — my portfolio desktop app

This is a JavaFX desktop app I'm building to show off my projects, research work and
achievements instead of just having a boring static webpage. It's also kind of a playground
for me to try out stuff (SQLite, threading, small UI effects) so don't expect it to be
perfectly clean everywhere.

Runs as a normal desktop window, not a website. Everything (your bio, your projects,
comments people leave, attached photos/PDFs) is saved locally in a `data/` folder next to
the app, backed by SQLite.

## What it actually does

- Shows your info (name, tagline, about, socials) pulled from a config file, not hardcoded
- Projects / Research / Achievements sections, each scrollable and searchable
- Owner mode: log in with a password to add, edit or remove entries
- You can attach a photo and/or a PDF to any entry
- Visitors can leave comments, either general ones or on a specific entry
- Clicking a section in the nav bar smooth-scrolls to it and swaps in a themed animated
  background for that section (own idea, still tweaking the visuals)
- A donate button wired up to bKash (currently pointed at the sandbox, not live)
- Basic cursor trail / hover effects because why not

## Running it

You need JDK 17+ and Maven. Then:

```
mvn javafx:run
```

First run will create a `data/` folder with a SQLite file and a `profile.properties` file
seeded with placeholder info + a default password. **Change the password before you show
this to anyone** — instructions are in the comments at the top of `Auth.java`, basically:

```
java src/main/java/portfolio/Auth.java "your new password"
```

then paste the hash it prints into `password_hash=` in `data/profile.properties`.

## Setting up your own info

Don't edit the Java files for this — everything personal lives in
`data/profile.properties` so it survives me pushing updates to the code. Open it and fill
in your name, tagline, about text, GitHub/LinkedIn links, email, WhatsApp number, etc.
It's plain text, self-explanatory.

### API key

The project's chatbot needs Gemini API key, and I couldn't give it there because github stops me from pushing that. Just copy the API key from here 
and put it in the dialogue box in the chatbot wanting the API key.
API key= AQ.Ab8RN6KPHdteBDmemxI0YAwGjNZwFgKIivbHZw1EjNd0lBRvcg

### Admin Password(Current): 

admin123

## Project layout

```
src/main/java/portfolio/
  PortfolioApp.java     the whole UI, main entry point, honestly kind of a monster file now
  Store.java            SQLite reads/writes
  Item.java             a project / research entry / achievement
  Comment.java          a comment, either general or tied to one item
  Profile.java          reads data/profile.properties
  Auth.java             password hashing for owner login
  BkashService.java     bKash sandbox payment flow
  Concurrency.java       shared thread pool so DB/network calls don't freeze the UI
  FireBackground.java / DetailBackground.java   the animated section backgrounds
  UiEffects.java        cursor trail, dialog animations, small polish stuff
```


## License

Personal project, not really meant to be reused as-is, but feel free to poke around the
code if it's useful to you.
