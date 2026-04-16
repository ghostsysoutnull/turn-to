package com.tas.neo.scripting;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuaScriptEngine implements ScriptEngine {

    @Override
    public void execute(String script, ScriptContext context) {
        execute(script, context, null);
    }

    /** Executes a script with both a ScriptContext binding and an AdventureScriptState binding. */
    public void execute(String script, ScriptContext context, AdventureScriptState scriptState) {
        Globals globals = JsePlatform.standardGlobals();

        sandbox(globals);
        bindContext(globals, context);
        if (scriptState != null) {
            bindState(globals, scriptState);
        }

        try {
            globals.load(script).call();
        } catch (Exception e) {
            context.onScriptError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private void sandbox(Globals globals) {
        globals.set("io", LuaValue.NIL);
        globals.set("os", LuaValue.NIL);
        globals.set("package", LuaValue.NIL);
        globals.set("require", LuaValue.NIL);
        globals.set("dofile", LuaValue.NIL);
        globals.set("load", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);
    }

    private void bindContext(Globals globals, ScriptContext context) {
        LuaTable ctx = new LuaTable();

        ctx.set("showMessage", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.showMessage(args.checkjstring(1));
                return LuaValue.NONE;
            }
        });

        ctx.set("modifyStat", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.modifyStat(args.checkjstring(1), args.checkint(2));
                return LuaValue.NONE;
            }
        });

        ctx.set("getStat", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.getStat(args.checkjstring(1)));
            }
        });

        ctx.set("modifyGold", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.modifyGold(args.checkint(1));
                return LuaValue.NONE;
            }
        });

        ctx.set("getGold", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.getGold());
            }
        });

        ctx.set("addItem", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() >= 2) {
                    context.addItem(args.checkjstring(1), args.checkint(2));
                } else {
                    context.addItem(args.checkjstring(1));
                }
                return LuaValue.NONE;
            }
        });

        ctx.set("removeItem", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if (args.narg() >= 2) {
                    context.removeItem(args.checkjstring(1), args.checkint(2));
                } else {
                    context.removeItem(args.checkjstring(1));
                }
                return LuaValue.NONE;
            }
        });

        ctx.set("hasItem", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.hasItem(args.checkjstring(1)));
            }
        });

        ctx.set("getItemCount", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.getItemCount(args.checkjstring(1)));
            }
        });

        ctx.set("navigateTo", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.navigateTo(args.checkint(1));
                return LuaValue.NONE;
            }
        });

        ctx.set("currentSection", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.currentSection());
            }
        });

        ctx.set("addChoice", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.addChoice(args.checkjstring(1), args.checkint(2));
                return LuaValue.NONE;
            }
        });

        ctx.set("hideChoice", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.hideChoice(args.checkjstring(1));
                return LuaValue.NONE;
            }
        });

        ctx.set("isPartyMemberActive", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(context.isPartyMemberActive(args.checkjstring(1)));
            }
        });

        ctx.set("addPartyMember", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.addPartyMember(args.checkjstring(1));
                return LuaValue.NONE;
            }
        });

        ctx.set("removePartyMember", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                context.removePartyMember(args.checkjstring(1));
                return LuaValue.NONE;
            }
        });

        globals.set("ctx", ctx);

        // Redirect print to ctx.showMessage
        globals.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) sb.append('\t');
                    sb.append(args.arg(i).tojstring());
                }
                context.showMessage(sb.toString());
                return LuaValue.NONE;
            }
        });
    }

    private void bindState(Globals globals, AdventureScriptState scriptState) {
        LuaTable state = new LuaTable();

        state.set("set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String key = args.checkjstring(1);
                LuaValue val = args.arg(2);
                if (val.isboolean()) {
                    scriptState.set(key, val.toboolean());
                } else if (val.isint()) {
                    scriptState.set(key, val.toint());
                } else {
                    scriptState.set(key, val.tojstring());
                }
                return LuaValue.NONE;
            }
        });

        state.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String key = args.checkjstring(1);
                Object value = scriptState.get(key);
                if (value == null) return LuaValue.NIL;
                if (value instanceof Boolean b) return LuaValue.valueOf(b);
                if (value instanceof Integer i) return LuaValue.valueOf(i);
                return LuaValue.valueOf(value.toString());
            }
        });

        state.set("has", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(scriptState.has(args.checkjstring(1)));
            }
        });

        globals.set("state", state);
    }
}
