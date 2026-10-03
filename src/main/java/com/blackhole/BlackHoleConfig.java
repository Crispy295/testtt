package com.blackhole;

/**
 * Все настройки мода в одном месте. Поменяйте значение и пересоберите мод.
 */
public final class BlackHoleConfig {
    private BlackHoleConfig() {}

    /** Перезарядка предмета: 10 секунд = 200 тиков. */
    public static final int COOLDOWN_TICKS = 200;
    /** Тратить ли предмет при броске (false = предмет многоразовый, ограничен только перезарядкой). */
    public static final boolean CONSUME_ITEM = false;
    /** Скорость броска. */
    public static final float THROW_SPEED = 1.6F;

    /** Время жизни чёрной дыры в тиках (220 = 11 секунд). */
    public static final int LIFETIME_TICKS = 220;
    /** Сколько тиков дыра "раскрывается". */
    public static final int GROW_TICKS = 24;
    /** Сколько последних тиков дыра схлопывается. */
    public static final int COLLAPSE_TICKS = 36;

    /** Радиус поглощения блоков. */
    public static final double BLOCK_RADIUS = 15.0;
    /** Радиус притяжения и поглощения мобов. */
    public static final double MOB_RADIUS = 25.0;
    /** За сколько тиков радиус поедания блоков вырастает до максимума. */
    public static final int EAT_TICKS = 120;

    /** Радиус тени (горизонта событий) в блоках - всё, что подошло ближе, погибает. */
    public static final float HORIZON_RADIUS = 1.9F;

    /** Лимиты на тик, чтобы сервер не лагал. */
    public static final int MAX_BLOCK_CHECKS_PER_TICK = 900;
    public static final int MAX_BLOCK_REMOVALS_PER_TICK = 64;
    public static final int MAX_DEBRIS_PER_TICK = 5;

    /** Бросивший игрок не притягивается и не погибает. */
    public static final boolean OWNER_IMMUNE = true;
    /** Поглощать ли выпавшие предметы и сферы опыта. */
    public static final boolean SWALLOW_ITEMS = true;
}
