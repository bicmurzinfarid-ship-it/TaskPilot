package TaskPilot.pre;

/**
 * Квадранты матрицы Эйзенхауэра (расширенная: 3 уровня важности × 2 уровня срочности).
 */
public enum EisenhowerQuadrant {
    URGENT_IMPORTANT,
    URGENT_SOMEWHAT_IMPORTANT,
    URGENT_NOT_IMPORTANT,
    NOT_URGENT_IMPORTANT,
    NOT_URGENT_SOMEWHAT_IMPORTANT,
    NOT_URGENT_NOT_IMPORTANT
}
