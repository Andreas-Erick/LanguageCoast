# Language Coast

A language-learning app: each language the learner studies is a coast, its vocabulary sits on islands, and the AI provider the learner picks writes practice material from it.

## Practice sheets

**Practice sheet**:
A printable set of numbered exercises written by the AI provider from one coast's cards, with the answers on their own page.
_Avoid_: Worksheet (in user-facing text), exercise sheet

**Sheet kind**:
What a practice sheet trains: *Translation* (the default, sentences to translate) or *Custom* (whatever the learner's brief asks for).
_Avoid_: Sheet type, mode, focus

**Brief**:
The learner's own words saying what a custom sheet should practise, such as "accusative vs dative after prepositions".
_Avoid_: Prompt, topic, focus

**Exercise**:
One numbered item on a practice sheet: a task for the learner and its answer. A sentence to translate is one kind of exercise.
_Avoid_: Question, item, sentence (unless it is a translation exercise)

**Exercise form**:
How a custom sheet's exercise is answered: *Gap fill* (a word missing from a sentence), *Multiple choice* (three options) or *Short answer* (a task with an empty box to write in).
_Avoid_: Exercise type, question type

**Grammar profile**:
What a language's grammar has (cases, articles, genders, plurals, verb features, particles, politeness), from which the example chips under the brief are made, so a language is only offered drills for grammar it has. A language without one gets the general chips on purpose.
_Avoid_: Language config, grammar settings
