# MyFinance R8 rules. Library rules (Room, Hilt, Tink, kotlinx.serialization) ship as consumer rules;
# app-specific keeps are added here when needed.

# Never ship log calls: they could leak financial data.
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}
