package run.endive.cm.types;

public final class PrimValType extends DefValType {

    public static PrimValType BOOL = new PrimValType(Kind.BOOL);
    public static PrimValType S8 = new PrimValType(Kind.S8);
    public static PrimValType U8 = new PrimValType(Kind.U8);
    public static PrimValType S16 = new PrimValType(Kind.S16);
    public static PrimValType U16 = new PrimValType(Kind.U16);
    public static PrimValType S32 = new PrimValType(Kind.S32);
    public static PrimValType U32 = new PrimValType(Kind.U32);
    public static PrimValType S64 = new PrimValType(Kind.S64);
    public static PrimValType U64 = new PrimValType(Kind.U64);
    public static PrimValType F32 = new PrimValType(Kind.F32);
    public static PrimValType F64 = new PrimValType(Kind.F64);
    public static PrimValType CHAR = new PrimValType(Kind.CHAR);
    public static PrimValType STRING = new PrimValType(Kind.STRING);
    public static PrimValType ERROR_CONTEXT = new PrimValType(Kind.ERROR_CONTEXT);

    private PrimValType(Kind kind) {
        super(kind);
    }
}
