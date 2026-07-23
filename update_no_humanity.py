import json

# Read the original file
with open(r'C:\Users\Eddie Thompson\moe\trivial-tile-trivia-portable\output\christmas_games_complete.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# New no_humanity section with mainstream references
new_no_humanity = [
    {
        "id": "christmas_games_no_humanity_1",
        "audience": "adults",
        "difficulty": "easy",
        "question": "This gift-stealing party game where your drunk aunt keeps stealing the Bed Bath & Beyond candle every single round",
        "answer": "What is White Elephant?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "dysfunctional_families"
        }
    },
    {
        "id": "christmas_games_no_humanity_2",
        "audience": "adults",
        "difficulty": "easy",
        "question": "The family-destroying board game that ends with your brother flipping the table and storming out on Christmas",
        "answer": "What is Monopoly?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "board_game_chaos"
        }
    },
    {
        "id": "christmas_games_no_humanity_3",
        "audience": "adults",
        "difficulty": "easy",
        "question": "This party game where you awkwardly act out 'Die Hard' while your Christian grandmother judges you silently",
        "answer": "What is Charades?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "awkward_family_moments"
        }
    },
    {
        "id": "christmas_games_no_humanity_4",
        "audience": "adults",
        "difficulty": "easy",
        "question": "The Christmas movie drinking game where you take a shot every time someone in a Hallmark movie is unreasonably white and wealthy",
        "answer": "What is the Hallmark Movie Drinking Game?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "drinking_games"
        }
    },
    {
        "id": "christmas_games_no_humanity_5",
        "audience": "adults",
        "difficulty": "easy",
        "question": "This offensive card game that made Grandma cry and leave the room at Christmas dinner 2023",
        "answer": "What is Cards Against Humanity?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "offensive_humor"
        }
    },
    {
        "id": "christmas_games_no_humanity_6",
        "audience": "adults",
        "difficulty": "easy",
        "question": "The streaming platform party game where your family spends 45 minutes scrolling to find a movie everyone agrees on before giving up",
        "answer": "What is Netflix Roulette?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "modern_problems"
        }
    },
    {
        "id": "christmas_games_no_humanity_7",
        "audience": "adults",
        "difficulty": "easy",
        "question": "This trivia game where your know-it-all cousin Googles every answer on their iPhone under the table",
        "answer": "What is Trivial Pursuit?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "cheating_families"
        }
    },
    {
        "id": "christmas_games_no_humanity_8",
        "audience": "adults",
        "difficulty": "easy",
        "question": "The Christmas karaoke tradition that ends with your divorced parents drunkenly singing 'Last Christmas' by Wham! while crying",
        "answer": "What is Holiday Karaoke?",
        "points": 200,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "family_drama"
        }
    },
    {
        "id": "christmas_games_no_humanity_9",
        "audience": "adults",
        "difficulty": "medium",
        "question": "This secret gift exchange where you realize your coworker spent exactly $10.00 at Target while you spent $50",
        "answer": "What is Secret Santa?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "workplace_dysfunction"
        }
    },
    {
        "id": "christmas_games_no_humanity_10",
        "audience": "adults",
        "difficulty": "medium",
        "question": "The drinking game where you take a shot every time someone mentions Trump or politics at Christmas dinner before your mom bans the topic",
        "answer": "What is the Political Christmas Drinking Game?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "political_chaos"
        }
    },
    {
        "id": "christmas_games_no_humanity_11",
        "audience": "adults",
        "difficulty": "medium",
        "question": "This word association party game that always ends with someone saying something accidentally racist and everyone getting quiet",
        "answer": "What is Taboo?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "awkward_moments"
        }
    },
    {
        "id": "christmas_games_no_humanity_12",
        "audience": "adults",
        "difficulty": "medium",
        "question": "The competitive game where your boomer dad refuses to believe he lost and demands a recount like it's the 2020 election",
        "answer": "What is Scrabble?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "sore_losers"
        }
    },
    {
        "id": "christmas_games_no_humanity_13",
        "audience": "adults",
        "difficulty": "medium",
        "question": "This photo-based Instagram game where you pretend your dysfunctional family is actually happy for social media likes",
        "answer": "What is the Christmas Photo Challenge?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "social_media_lies"
        }
    },
    {
        "id": "christmas_games_no_humanity_14",
        "audience": "adults",
        "difficulty": "medium",
        "question": "The TikTok dance challenge your Gen Z niece forces everyone to do while your millennial self cries about aging",
        "answer": "What is the Christmas TikTok Challenge?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "generation_gap"
        }
    },
    {
        "id": "christmas_games_no_humanity_15",
        "audience": "adults",
        "difficulty": "medium",
        "question": "This guessing game where you're supposed to identify Christmas songs but Mariah Carey's 'All I Want For Christmas' is the only answer anyone knows",
        "answer": "What is Name That Christmas Tune?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "music_obsession"
        }
    },
    {
        "id": "christmas_games_no_humanity_16",
        "audience": "adults",
        "difficulty": "medium",
        "question": "The team game where you realize your family is so dysfunctional you can't even work together to build a gingerbread house without arguing",
        "answer": "What is Gingerbread House Competition?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "competitive_chaos"
        }
    },
    {
        "id": "christmas_games_no_humanity_17",
        "audience": "adults",
        "difficulty": "medium",
        "question": "This Amazon Alexa game that won't stop suggesting you buy more stuff during the most expensive month of the year",
        "answer": "What is Alexa Christmas Trivia?",
        "points": 400,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "corporate_surveillance"
        }
    },
    {
        "id": "christmas_games_no_humanity_18",
        "audience": "adults",
        "difficulty": "hard",
        "question": "The Zoom party game your company forced everyone to do in 2020 where half the people 'had connection issues' to avoid participating",
        "answer": "What is Virtual Christmas Party Games?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "pandemic_trauma"
        }
    },
    {
        "id": "christmas_games_no_humanity_19",
        "audience": "adults",
        "difficulty": "hard",
        "question": "This gift exchange game that turned into a Facebook Marketplace resale operation the day after Christmas",
        "answer": "What is Yankee Swap?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "capitalism"
        }
    },
    {
        "id": "christmas_games_no_humanity_20",
        "audience": "adults",
        "difficulty": "hard",
        "question": "The drinking game where you take a shot every time someone at dinner mentions COVID vaccines and ruins Christmas 2021-2023",
        "answer": "What is the COVID Christmas Drinking Game?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "pandemic_politics"
        }
    },
    {
        "id": "christmas_games_no_humanity_21",
        "audience": "adults",
        "difficulty": "hard",
        "question": "This betting game where your uncle loses $500 on DraftKings during the Christmas Day NBA games instead of spending time with family",
        "answer": "What is Sports Betting?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "gambling_addiction"
        }
    },
    {
        "id": "christmas_games_no_humanity_22",
        "audience": "adults",
        "difficulty": "hard",
        "question": "The party game where you have to explain to Grandma what OnlyFans is after someone made a joke about it",
        "answer": "What is Awkward Christmas Explanations?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "generation_gap"
        }
    },
    {
        "id": "christmas_games_no_humanity_23",
        "audience": "adults",
        "difficulty": "hard",
        "question": "This Tesla driving game your tech bro brother-in-law won't shut up about during Christmas dinner while everyone else is broke",
        "answer": "What is Tesla Arcade?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "wealth_inequality"
        }
    },
    {
        "id": "christmas_games_no_humanity_24",
        "audience": "adults",
        "difficulty": "hard",
        "question": "The mobile game where everyone at Christmas dinner is staring at their phones instead of talking to each other",
        "answer": "What is Candy Crush?",
        "points": 600,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "phone_addiction"
        }
    },
    {
        "id": "christmas_games_no_humanity_25",
        "audience": "adults",
        "difficulty": "expert",
        "question": "This competitive cooking game on your smart TV that your family watches instead of actually cooking together like a functional family",
        "answer": "What is The Great British Baking Show?",
        "points": 800,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "passive_entertainment"
        }
    },
    {
        "id": "christmas_games_no_humanity_26",
        "audience": "adults",
        "difficulty": "expert",
        "question": "The Venmo gift exchange where you can see exactly who's broke based on who sends you $15 with the Christmas tree emoji",
        "answer": "What is Digital Secret Santa?",
        "points": 800,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "financial_embarrassment"
        }
    },
    {
        "id": "christmas_games_no_humanity_27",
        "audience": "adults",
        "difficulty": "expert",
        "question": "This murder mystery party game that's less stressful than an actual Christmas dinner with your in-laws",
        "answer": "What is a Murder Mystery Party?",
        "points": 800,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "family_avoidance"
        }
    },
    {
        "id": "christmas_games_no_humanity_28",
        "audience": "adults",
        "difficulty": "expert",
        "question": "The party game where your therapy-needing family argues about whether Die Hard is actually a Christmas movie for the 10th year in a row",
        "answer": "What is the Die Hard Debate?",
        "points": 800,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "exhausting_traditions"
        }
    },
    {
        "id": "christmas_games_no_humanity_29",
        "audience": "adults",
        "difficulty": "expert",
        "question": "This escape room experience your family did to literally escape from each other for an hour on Christmas",
        "answer": "What is a Christmas Escape Room?",
        "points": 800,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "family_exhaustion"
        }
    },
    {
        "id": "christmas_games_no_humanity_30",
        "audience": "adults",
        "difficulty": "master",
        "question": "The real Christmas game everyone plays: pretending you're happy to be there while secretly counting down until you can leave and watch Netflix alone",
        "answer": "What is Faking Holiday Cheer?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "emotional_survival"
        }
    },
    {
        "id": "christmas_games_no_humanity_31",
        "audience": "adults",
        "difficulty": "master",
        "question": "This competitive game where you see who can avoid talking about their student loan debt the longest at Christmas dinner",
        "answer": "What is the Millennial Christmas Avoidance Game?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "economic_anxiety"
        }
    },
    {
        "id": "christmas_games_no_humanity_32",
        "audience": "adults",
        "difficulty": "master",
        "question": "The Instagram story game where you post your Christmas morning while hiding the fact you can't afford rent in January",
        "answer": "What is Performative Happiness?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "social_media_facade"
        }
    },
    {
        "id": "christmas_games_no_humanity_33",
        "audience": "adults",
        "difficulty": "master",
        "question": "This game everyone at Target plays the week before Christmas: finding literally anything that's not sold out because you procrastinated",
        "answer": "What is Last-Minute Christmas Shopping?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "retail_chaos"
        }
    },
    {
        "id": "christmas_games_no_humanity_34",
        "audience": "adults",
        "difficulty": "master",
        "question": "The drinking game you play alone on December 26th to cope with the credit card bill you'll get in January",
        "answer": "What is Post-Christmas Financial Panic?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "economic_reality"
        }
    },
    {
        "id": "christmas_games_no_humanity_35",
        "audience": "adults",
        "difficulty": "master",
        "question": "This competitive game where divorced parents see who can outspend each other on gifts to win the kids' affection",
        "answer": "What is Guilt Gift Olympics?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "divorce_warfare"
        }
    },
    {
        "id": "christmas_games_no_humanity_36",
        "audience": "adults",
        "difficulty": "master",
        "question": "The ultimate Christmas party game: seeing how many times you can check your phone to avoid conversation with relatives you see once a year",
        "answer": "What is Strategic Phone Staring?",
        "points": 1000,
        "category": "Holiday Games",
        "metadata": {
            "topic": "Dark Humor Party Games",
            "age_appropriate": False,
            "theme": "social_avoidance"
        }
    }
]

# Replace the no_humanity section
data['no_humanity'] = new_no_humanity

# Write back to file
with open(r'C:\Users\Eddie Thompson\moe\trivial-tile-trivia-portable\output\christmas_games_complete.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=2, ensure_ascii=False)

print("Successfully updated no_humanity section with 36 mainstream reference questions!")
print("All references follow the 'Would My Mom Know This?' test")
