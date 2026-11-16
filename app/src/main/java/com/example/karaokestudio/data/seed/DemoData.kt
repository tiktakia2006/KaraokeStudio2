package com.example.karaokestudio.data.seed

import com.example.karaokestudio.data.local.entity.Song

object DemoData {

    val songs: List<Song> = listOf(
        Song(
            title = "Ранкова хвиля", artist = "Demo Band", durationMs = 48_000,
            genre = "Поп", coverSource = "gradient:0", audioSource = "demo:morning-wave", isDemo = true, slug = "morning-wave",
            lyrics = """
                [00:00.00]
                [00:04.00]Світанок розливає золото на дах
                [00:09.00]Місто потихеньку скидає сон
                [00:14.00]Я ловлю хвилю, що біжить до мене
                [00:19.00]І співаю разом із вітром
                [00:24.00]Ой, ранок, ранок, відкривай мені день
                [00:29.00]Хай у серці лунає наш мотив
                [00:34.00]Крок за кроком, нота за нотою
                [00:39.00]Ми підемо назустріч сонцю
                [00:44.00]
            """.trimIndent()
        ),
        Song(
            title = "Нічне місто", artist = "Test Artist", durationMs = 50_000,
            genre = "Інді", coverSource = "gradient:1", audioSource = "demo:night-city", isDemo = true, slug = "night-city",
            lyrics = """
                [00:00.00]
                [00:05.00]Ліхтарі пишуть золоті рядки
                [00:10.00]На мокрому склі моїх думок
                [00:15.00]Нічне місто не питає, хто я
                [00:20.00]Воно просто слухає мій крок
                [00:25.00]Тихо, тихо, світло в темряві
                [00:30.00]Ми з тобою ще не спимо
                [00:35.00]Хай триває ця нічна дорога
                [00:40.00]До самого ранку, до ранку
                [00:46.00]
            """.trimIndent()
        ),
        Song(
            title = "Літня дорога", artist = "Road Trip (демо)", durationMs = 44_000,
            genre = "Рок", coverSource = "gradient:2", audioSource = "demo:summer-road", isDemo = true, slug = "summer-road",
            lyrics = """
                [00:00.00]
                [00:04.00]Вікна вниз, і вітер у волоссі
                [00:08.00]Попереду літо без кінця
                [00:12.00]Радіо гуде, ми підспіваємо
                [00:16.00]Не питай, куди веде дорога
                [00:20.00]Гей, гей, ми їдемо вперед
                [00:24.00]Сонце над капотом, друзі поруч
                [00:28.00]Кожен поворот — новий куплет
                [00:32.00]А попереду лише світло
                [00:38.00]
            """.trimIndent()
        ),
        Song(
            title = "Дощ за вікном", artist = "Demo Band", durationMs = 52_000,
            genre = "Балада", coverSource = "gradient:3", audioSource = "demo:rain-window", isDemo = true, slug = "rain-window",
            lyrics = """
                [00:00.00]
                [00:06.00]Дощ за вікном рахує краплі
                [00:12.00]Чай парує на столі
                [00:18.00]Я згадую розмови давні
                [00:24.00]Як теплі вогники вдалині
                [00:30.00]Нехай іде, нехай співає
                [00:36.00]Ця тиха осінь у мені
                [00:42.00]Все минає, все вертається
                [00:47.00]
            """.trimIndent()
        ),
        Song(
            title = "Neon Dance", artist = "Test Artist", durationMs = 40_000,
            genre = "Денс", coverSource = "gradient:4", audioSource = "demo:neon-dance", isDemo = true, slug = "neon-dance",
            lyrics = """
                [00:00.00]
                [00:04.00]Lights are rising on the avenue
                [00:08.00]Every heartbeat knows what to do
                [00:12.00]Move your body, feel the sound
                [00:16.00]We are the dancers of this town
                [00:20.00]Neon, neon, take me higher
                [00:24.00]Set the whole wide night on fire
                [00:28.00]Hold the rhythm, don't let go
                [00:32.00]Dance with me and steal the show
                [00:37.00]
            """.trimIndent()
        ),
        Song(
            title = "Тиха річка", artist = "Folk Test", durationMs = 48_000,
            genre = "Фолк", coverSource = "gradient:5", audioSource = "demo:quiet-river", isDemo = true, slug = "quiet-river",
            lyrics = """
                [00:00.00]
                [00:05.00]Тиха річка тече між берегів
                [00:10.00]Несе до моря білі пелюстки
                [00:15.00]Вербо над водою, розкажи мені
                [00:20.00]Де шукати щастя, де знайти
                [00:25.00]Тихо, тихо тече вода
                [00:30.00]Хоч далеко, але вона жива
                [00:35.00]Заспіваю тобі, мила річко
                [00:40.00]Мою просту українську пісню
                [00:45.00]
            """.trimIndent()
        )
    )

    data class DemoPerformance(val songIndex: Int, val daysAgo: Int, val durationMs: Long, val score: Int)

    val performances: List<DemoPerformance> = listOf(
        DemoPerformance(0, 13, 48_000, 62),
        DemoPerformance(1, 11, 50_000, 71),
        DemoPerformance(0, 9, 48_000, 78),
        DemoPerformance(2, 7, 44_000, 55),
        DemoPerformance(3, 5, 52_000, 83),
        DemoPerformance(4, 3, 40_000, 90),
        DemoPerformance(0, 2, 48_000, 86),
        DemoPerformance(5, 1, 48_000, 74)
    )
}
