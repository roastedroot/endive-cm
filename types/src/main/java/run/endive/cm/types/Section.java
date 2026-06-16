package run.endive.cm.types;

public abstract class Section {
    private final int id;

    Section(long id) {
        this.id = (int) id;
    }

    public int sectionId() {
        return id;
    }
}
