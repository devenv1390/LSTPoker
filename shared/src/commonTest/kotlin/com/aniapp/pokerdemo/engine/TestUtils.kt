package com.aniapp.pokerdemo.engine

fun testCards(s: String): List<Card> = s.split(" ").map { tok ->
    val rank = when (val c = tok[0]) {
        'A' -> 14; 'K' -> 13; 'Q' -> 12; 'J' -> 11; 'T' -> 10
        else -> c.digitToInt()
    }
    val suit = when (tok[1]) {
        's' -> Suit.SPADES; 'h' -> Suit.HEARTS; 'd' -> Suit.DIAMONDS; else -> Suit.CLUBS
    }
    Card(rank, suit)
}

// 发牌顺序：座位0起每人2张、烧牌、翻牌3、烧牌、转牌、烧牌、河牌
fun stackedDeck(vararg holes: String, board: String): Deck {
    val b = testCards(board)
    val list = holes.flatMap { testCards(it) } +
        listOf(Card(2, Suit.SPADES)) + b.take(3) +
        listOf(Card(3, Suit.SPADES)) + listOf(b[3]) +
        listOf(Card(4, Suit.SPADES)) + listOf(b[4])
    return Deck(list)
}

fun huEngine(
    config: GameConfig = GameConfig(),
    random: kotlin.random.Random = kotlin.random.Random(1),
    newDeck: (() -> Deck)? = null
): TableEngine = TableEngine(
    config = config,
    players = listOf(PlayerInit("玩家"), PlayerInit("AI")),
    random = random,
    newDeck = newDeck ?: { Deck(random) }
)