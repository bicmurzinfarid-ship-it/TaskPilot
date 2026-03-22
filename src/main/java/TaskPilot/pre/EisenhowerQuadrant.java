package TaskPilot.pre;

/**
 * Квадранты матрицы Эйзенхауэра.
 * Классификация задач по срочности и важности.
 */
public enum EisenhowerQuadrant {
    /** Срочно и важно — делать в первую очередь */
    URGENT_IMPORTANT,
    /** Не срочно, но важно — планировать */
    NOT_URGENT_IMPORTANT,
    /** Срочно, но не важно — делегировать */
    URGENT_NOT_IMPORTANT,
    /** Не срочно и не важно — устранить/отложить */
    NOT_URGENT_NOT_IMPORTANT
}
