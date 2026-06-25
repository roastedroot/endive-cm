package run.endive.cm.types;

public final class PrimValType extends DefValType {

    public static PrimValType BOOL = new PrimValType(ID.BOOL);
    public static PrimValType S8 = new PrimValType(ID.S8);
    public static PrimValType U8 = new PrimValType(ID.U8);
    public static PrimValType S16 = new PrimValType(ID.S16);
    public static PrimValType U16 = new PrimValType(ID.U16);
    public static PrimValType S32 = new PrimValType(ID.S32);
    public static PrimValType U32 = new PrimValType(ID.U32);
    public static PrimValType S64 = new PrimValType(ID.S64);
    public static PrimValType U64 = new PrimValType(ID.U64);
    public static PrimValType F32 = new PrimValType(ID.F32);
    public static PrimValType F64 = new PrimValType(ID.F64);
    public static PrimValType CHAR = new PrimValType(ID.CHAR);
    public static PrimValType STRING = new PrimValType(ID.STRING);
    public static PrimValType ERROR_CONTEXT = new PrimValType(ID.ERROR_CONTEXT);

    private PrimValType(int id) {
        super(id);
    }
}
