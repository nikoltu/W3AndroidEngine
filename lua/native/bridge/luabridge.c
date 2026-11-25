/*
** JNI bridge of com.etheller.warsmash.lua.LuaState.
**
** Rules kept here:
**  - Lua errors never cross a Java frame: Java is only called from the C functions below, and an
**    error raised after such a call is raised once the call has returned.
**  - Everything that can run Lua code (call, tostring) is protected; the other entry points only
**    use raw accessors, so no metamethod and no error can run through them.
**  - Strings cross as byte arrays (Lua strings are bytes, JNI strings are modified UTF-8).
**  - The JNIEnv is valid only during a native call, so it is stored on entry to the functions that run Lua.
**  - Everything that can allocate (pushing a string or a closure, growing a table) runs under wc_protect, so an
**    out-of-memory error comes back as a failure result instead of unwinding through Java frames or panicking.
*/
#include <jni.h>
#include <limits.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

#if defined(_WIN32)
#define WC_EXPORT __declspec(dllexport)
#else
#define WC_EXPORT __attribute__((visibility("default")))
#endif
#define JFUNC(name) WC_EXPORT JNICALL Java_com_etheller_warsmash_lua_LuaState_##name

typedef struct JState {
  wc_ctx base;  /* first: the context pointer of the Lua state points here */
  lua_State *L;
  JNIEnv *env;
  jobject self;
  jmethodID dispatch;
  jmethodID random;
  jmethodID print;
  jmethodID setPending;
  int64_t mem_used;
  int64_t mem_limit;
  int64_t op_limit;  /* instructions a call or a resume from Java may run, 0 for no limit */
  int64_t op_left;
  int op_step;
  char *last_error;  /* the message of the last failed persist / unpersist */
} JState;

static void *l_alloc(void *ud, void *ptr, size_t osize, size_t nsize) {
  JState *js = (JState *)ud;
  void *np;
  if (ptr == NULL) osize = 0;
  if (nsize == 0) {
    if (ptr != NULL) {
      js->mem_used -= (int64_t)osize;
      free(ptr);
    }
    return NULL;
  }
  if (js->mem_limit > 0 && nsize > osize && js->mem_used + (int64_t)(nsize - osize) > js->mem_limit)
    return NULL;
  np = realloc(ptr, nsize);
  if (np != NULL) js->mem_used += (int64_t)nsize - (int64_t)osize;
  return np;
}

static jbyteArray to_jbytes(JNIEnv *env, const char *s, size_t n) {
  jbyteArray a = (*env)->NewByteArray(env, (jsize)n);
  if (a != NULL && n > 0) (*env)->SetByteArrayRegion(env, a, 0, (jsize)n, (const jbyte *)s);
  return a;
}

/* a Java exception is pending after a call into Java: hand it to the Java side and raise a Lua error */
static int raise_java(lua_State *L, JState *js) {
  jthrowable ex = (*js->env)->ExceptionOccurred(js->env);
  (*js->env)->ExceptionClear(js->env);
  if (ex != NULL) {
    (*js->env)->CallVoidMethod(js->env, js->self, js->setPending, ex);
    (*js->env)->DeleteLocalRef(js->env, ex);
    (*js->env)->ExceptionClear(js->env);
  }
  return luaL_error(L, "exception in a Java callback");
}

/* Lua function: a registered Java function; upvalue 1 is its id */
static int java_function(lua_State *L) {
  JState *js = (JState *)wc_getctx(L);
  jint id = (jint)lua_tointeger(L, lua_upvalueindex(1));
  jint n = (*js->env)->CallIntMethod(js->env, js->self, js->dispatch, id, (jlong)(intptr_t)L);
  if ((*js->env)->ExceptionCheck(js->env)) return raise_java(L, js);
  if (n == INT_MIN) return luaL_error(L, "not enough memory");  /* no error value could be pushed */
  if (n < -1) {  /* the Java function yields -2 - n values */
    int k = -2 - n;
    if (k > lua_gettop(L)) return luaL_error(L, "bad number of yielded values");
    return lua_yield(L, k);
  }
  if (n < 0) return lua_error(L);  /* the Java side left the error value on the stack */
  return n;
}

/* count hook: the instruction budget of the running call or resume is used up */
static void count_hook(lua_State *L, lua_Debug *ar) {
  JState *js = (JState *)wc_getctx(L);
  (void)ar;
  js->op_left -= js->op_step;
  if (js->op_left <= 0) {
    lua_pushliteral(L, "operation limit");
    lua_error(L);
  }
}

static void apply_hook(JState *js, lua_State *L) {
  if (js->op_limit > 0) lua_sethook(L, count_hook, LUA_MASKCOUNT, js->op_step);
  else lua_sethook(L, NULL, 0, 0);
}

static uint64_t java_random(lua_State *L, wc_ctx *ctx) {
  JState *js = (JState *)ctx;
  jlong r = (*js->env)->CallLongMethod(js->env, js->self, js->random);
  if ((*js->env)->ExceptionCheck(js->env)) raise_java(L, js);
  return (uint64_t)r;
}

static void java_print(lua_State *L, const char *s, size_t l) {
  JState *js = (JState *)wc_getctx(L);
  jbyteArray a = to_jbytes(js->env, s, l);
  if (a == NULL) {
    (*js->env)->ExceptionClear(js->env);
    luaL_error(L, "not enough memory");
    return;
  }
  (*js->env)->CallVoidMethod(js->env, js->self, js->print, a);
  (*js->env)->DeleteLocalRef(js->env, a);
  if ((*js->env)->ExceptionCheck(js->env)) raise_java(L, js);
}

static int msgh(lua_State *L) {
  const char *msg = lua_tostring(L, 1);
  if (msg == NULL) {
    if (luaL_callmeta(L, 1, "__tostring") && lua_type(L, -1) == LUA_TSTRING)
      return 1;
    msg = lua_pushfstring(L, "(error object is a %s value)", luaL_typename(L, 1));
  }
  luaL_traceback(L, L, msg, 1);
  return 1;
}

static int tostring_helper(lua_State *L) {
  luaL_tolstring(L, 1, NULL);
  return 1;
}

static jbyteArray pop_message(JNIEnv *env, lua_State *L) {
  size_t n = 0;
  const char *m = lua_tolstring(L, -1, &n);
  jbyteArray r = m != NULL ? to_jbytes(env, m, n) : to_jbytes(env, "error", 5);
  lua_pop(L, 1);
  return r;
}

typedef struct {
  const char *s;
  size_t n;
  jlong v;
  int idx;
  int type;
} Op;

/* runs an allocating operation; on failure the stack is cut back to what it was before, minus the values the
   operation would have consumed */
static int guarded(lua_State *L, wc_pfn f, Op *op, int consumed) {
  int top = lua_gettop(L);
  if (wc_protect(L, f, op) != LUA_OK) {
    lua_settop(L, top - consumed);
    return 0;
  }
  return 1;
}

static void op_pushbytes(lua_State *L, void *ud) { Op *o = (Op *)ud; lua_pushlstring(L, o->s, o->n); }
static void op_pushfunction(lua_State *L, void *ud) { Op *o = (Op *)ud; lua_pushinteger(L, (lua_Integer)o->v); lua_pushcclosure(L, java_function, 1); }
static void op_newtable(lua_State *L, void *ud) { (void)ud; lua_newtable(L); }
static void op_getfield(lua_State *L, void *ud) { Op *o = (Op *)ud; lua_pushstring(L, o->s); o->type = lua_rawget(L, o->idx); }
static void op_setfield(lua_State *L, void *ud) { Op *o = (Op *)ud; lua_pushstring(L, o->s); lua_insert(L, -2); lua_rawset(L, o->idx); }
static void op_seti(lua_State *L, void *ud) { Op *o = (Op *)ud; lua_rawseti(L, o->idx, (lua_Integer)o->v); }
static void op_rawset(lua_State *L, void *ud) { Op *o = (Op *)ud; lua_rawset(L, o->idx); }
static void op_getglobal(lua_State *L, void *ud) {
  Op *o = (Op *)ud;
  lua_rawgeti(L, LUA_REGISTRYINDEX, LUA_RIDX_GLOBALS);
  lua_pushstring(L, o->s);
  o->type = lua_rawget(L, -2);
  lua_remove(L, -2);
}
static void op_setglobal(lua_State *L, void *ud) {
  Op *o = (Op *)ud;
  lua_rawgeti(L, LUA_REGISTRYINDEX, LUA_RIDX_GLOBALS);
  lua_pushstring(L, o->s);
  lua_pushvalue(L, -3);
  lua_rawset(L, -3);
  lua_pop(L, 2);
}
static void op_tolstring(lua_State *L, void *ud) { Op *o = (Op *)ud; o->s = lua_tolstring(L, o->idx, &o->n); }

static void op_openlibs(lua_State *L, void *ud) { (void)ud; luaL_openlibs(L); }

#define STATE(p) ((lua_State *)(intptr_t)(p))
#define JS(p) ((JState *)(intptr_t)(p))

jlong JFUNC(nNew)(JNIEnv *env, jclass cls, jobject self, jlong memLimit) {
  JState *js = (JState *)calloc(1, sizeof(JState));
  jclass k;
  (void)cls;
  if (js == NULL) return 0;
  wc_ctx_init(&js->base);
  js->base.print = java_print;
  js->mem_limit = memLimit;
  js->env = env;
  js->self = (*env)->NewGlobalRef(env, self);
  k = (*env)->GetObjectClass(env, self);
  js->dispatch = (*env)->GetMethodID(env, k, "dispatch", "(IJ)I");
  js->random = (*env)->GetMethodID(env, k, "randomHook", "()J");
  js->print = (*env)->GetMethodID(env, k, "printHook", "([B)V");
  js->setPending = (*env)->GetMethodID(env, k, "setPending", "(Ljava/lang/Throwable;)V");
  (*env)->DeleteLocalRef(env, k);
  if (js->self == NULL || js->dispatch == NULL || js->random == NULL || js->print == NULL || js->setPending == NULL) {
    if (js->self != NULL) (*env)->DeleteGlobalRef(env, js->self);
    free(js);
    return 0;
  }
  js->L = lua_newstate(l_alloc, js);
  if (js->L == NULL) {
    (*env)->DeleteGlobalRef(env, js->self);
    free(js);
    return 0;
  }
  *(wc_ctx **)lua_getextraspace(js->L) = &js->base;
  if (wc_protect(js->L, op_openlibs, NULL) != LUA_OK) {  /* a memory limit too small for the libraries */
    lua_close(js->L);
    (*env)->DeleteGlobalRef(env, js->self);
    free(js);
    return 0;
  }
  return (jlong)(intptr_t)js;
}

jlong JFUNC(nMain)(JNIEnv *env, jclass cls, jlong js) {
  (void)env; (void)cls;
  return (jlong)(intptr_t)JS(js)->L;
}

void JFUNC(nClose)(JNIEnv *env, jclass cls, jlong p) {
  JState *js = JS(p);
  (void)cls;
  js->env = env;
  lua_close(js->L);  /* no __gc runs, so no Java is called */
  (*env)->DeleteGlobalRef(env, js->self);
  free(js->last_error);
  free(js);
}

void JFUNC(nSetRandomHook)(JNIEnv *env, jclass cls, jlong p, jboolean on) {
  (void)env; (void)cls;
  JS(p)->base.random_hook = on ? java_random : NULL;
}

void JFUNC(nSetTime)(JNIEnv *env, jclass cls, jlong p, jlong seconds, jdouble clock) {
  (void)env; (void)cls;
  JS(p)->base.time_seconds = (LUA_INTEGER)seconds;
  JS(p)->base.clock_seconds = clock;
}

jlong JFUNC(nMemoryUsed)(JNIEnv *env, jclass cls, jlong p) {
  (void)env; (void)cls;
  return (jlong)JS(p)->mem_used;
}

void JFUNC(nSetMemoryLimit)(JNIEnv *env, jclass cls, jlong p, jlong limit) {
  (void)env; (void)cls;
  JS(p)->mem_limit = limit;
}

void JFUNC(nCollect)(JNIEnv *env, jclass cls, jlong p) {
  (void)env; (void)cls;
  lua_gc(STATE(p), LUA_GCCOLLECT, 0);
}

/* returns null when the chunk was pushed, otherwise the error message */
jbyteArray JFUNC(nLoad)(JNIEnv *env, jclass cls, jlong p, jbyteArray code, jbyteArray name) {
  lua_State *L = STATE(p);
  jsize n = (*env)->GetArrayLength(env, code);
  jsize nn = (*env)->GetArrayLength(env, name);
  char *buf = (char *)malloc((size_t)n + 1);
  char *nm = (char *)malloc((size_t)nn + 1);
  int rc;
  (void)cls;
  if (buf == NULL || nm == NULL || !lua_checkstack(L, 2)) {
    free(buf);
    free(nm);
    return to_jbytes(env, "not enough memory", 17);
  }
  (*env)->GetByteArrayRegion(env, code, 0, n, (jbyte *)buf);
  (*env)->GetByteArrayRegion(env, name, 0, nn, (jbyte *)nm);
  nm[nn] = 0;
  rc = luaL_loadbufferx(L, buf, (size_t)n, nm, "t");
  free(buf);
  free(nm);
  return rc == LUA_OK ? NULL : pop_message(env, L);
}

/* calls the function below the arguments; null on success, otherwise the message with the traceback */
jbyteArray JFUNC(nCall)(JNIEnv *env, jclass cls, jlong jsp, jlong p, jint nargs, jint nresults, jboolean reset) {
  JState *js = JS(jsp);
  lua_State *L = STATE(p);
  int base = lua_gettop(L) - nargs;
  int rc;
  (void)cls;
  js->env = env;
  if (reset) js->op_left = js->op_limit;
  if (!lua_checkstack(L, 1)) return to_jbytes(env, "stack overflow", 14);
  lua_pushcfunction(L, msgh);
  lua_insert(L, base);
  rc = lua_pcall(L, nargs, nresults, base);
  lua_remove(L, base);
  return rc == LUA_OK ? NULL : pop_message(env, L);
}

/* the text of a value as tostring makes it (the error message if its __tostring failed) */
jbyteArray JFUNC(nToDisplay)(JNIEnv *env, jclass cls, jlong jsp, jlong p, jint idx) {
  JState *js = JS(jsp);
  lua_State *L = STATE(p);
  jbyteArray r;
  size_t n;
  const char *s;
  (void)cls;
  js->env = env;
  if (!lua_checkstack(L, 3)) return to_jbytes(env, "stack overflow", 14);
  idx = lua_absindex(L, idx);
  lua_pushcfunction(L, tostring_helper);
  lua_pushvalue(L, idx);
  if (lua_pcall(L, 1, 1, 0) != LUA_OK) return pop_message(env, L);
  s = lua_tolstring(L, -1, &n);
  r = to_jbytes(env, s != NULL ? s : "", s != NULL ? n : 0);
  lua_pop(L, 1);
  return r;
}

jint JFUNC(nGetTop)(JNIEnv *env, jclass cls, jlong p) {
  (void)env; (void)cls;
  return lua_gettop(STATE(p));
}

void JFUNC(nSetTop)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  lua_settop(STATE(p), idx);
}

jboolean JFUNC(nCheckStack)(JNIEnv *env, jclass cls, jlong p, jint n) {
  (void)env; (void)cls;
  return lua_checkstack(STATE(p), n) ? JNI_TRUE : JNI_FALSE;
}

void JFUNC(nPushValue)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  lua_pushvalue(STATE(p), idx);
}

void JFUNC(nRemove)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  lua_remove(STATE(p), idx);
}

void JFUNC(nPushNil)(JNIEnv *env, jclass cls, jlong p) {
  (void)env; (void)cls;
  lua_pushnil(STATE(p));
}

void JFUNC(nPushBoolean)(JNIEnv *env, jclass cls, jlong p, jboolean b) {
  (void)env; (void)cls;
  lua_pushboolean(STATE(p), b);
}

void JFUNC(nPushInteger)(JNIEnv *env, jclass cls, jlong p, jlong v) {
  (void)env; (void)cls;
  lua_pushinteger(STATE(p), (lua_Integer)v);
}

void JFUNC(nPushNumber)(JNIEnv *env, jclass cls, jlong p, jdouble v) {
  (void)env; (void)cls;
  lua_pushnumber(STATE(p), (lua_Number)v);
}

/* a light userdata is a pointer value that compares and hashes by value and survives persisting; the Java side
   stores an id in it */
void JFUNC(nPushLightUserdata)(JNIEnv *env, jclass cls, jlong p, jlong v) {
  (void)env; (void)cls;
  lua_pushlightuserdata(STATE(p), (void *)(intptr_t)v);
}

jlong JFUNC(nToLightUserdata)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return lua_type(STATE(p), idx) == LUA_TLIGHTUSERDATA ? (jlong)(intptr_t)lua_touserdata(STATE(p), idx) : 0;
}

jboolean JFUNC(nPushBytes)(JNIEnv *env, jclass cls, jlong p, jbyteArray a) {
  lua_State *L = STATE(p);
  jsize n = (*env)->GetArrayLength(env, a);
  char *buf = (char *)malloc((size_t)n + 1);
  Op op;
  jboolean ok;
  (void)cls;
  if (buf == NULL) return JNI_FALSE;
  (*env)->GetByteArrayRegion(env, a, 0, n, (jbyte *)buf);
  op.s = buf;
  op.n = (size_t)n;
  ok = guarded(L, op_pushbytes, &op, 0) ? JNI_TRUE : JNI_FALSE;
  free(buf);
  return ok;
}

jboolean JFUNC(nPushFunction)(JNIEnv *env, jclass cls, jlong p, jint id) {
  Op op;
  (void)env; (void)cls;
  op.v = id;
  return guarded(STATE(p), op_pushfunction, &op, 0) ? JNI_TRUE : JNI_FALSE;
}

jint JFUNC(nType)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return lua_type(STATE(p), idx);
}

jboolean JFUNC(nToBoolean)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return lua_toboolean(STATE(p), idx) ? JNI_TRUE : JNI_FALSE;
}

jboolean JFUNC(nIsInteger)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return lua_isinteger(STATE(p), idx) ? JNI_TRUE : JNI_FALSE;
}

/* a number, or a string that converts to one */
jboolean JFUNC(nIsNumber)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return lua_isnumber(STATE(p), idx) ? JNI_TRUE : JNI_FALSE;
}

static void op_where(lua_State *L, void *ud) { (void)ud; luaL_where(L, 1); }

/* the position ("chunk:line: ") of the Lua code that called the running function, empty if none; what luaL_error puts
   in front of its message */
jbyteArray JFUNC(nWhere)(JNIEnv *env, jclass cls, jlong p) {
  lua_State *L = STATE(p);
  size_t n = 0;
  const char *s;
  jbyteArray r;
  (void)cls;
  if (!lua_checkstack(L, 2) || wc_protect(L, op_where, NULL) != LUA_OK) return to_jbytes(env, "", 0);
  s = lua_tolstring(L, -1, &n);
  r = to_jbytes(env, s != NULL ? s : "", s != NULL ? n : 0);
  lua_pop(L, 1);
  return r;
}

/* integer value of a number (floats with an exact integer value too, strings that look like one), else 0 */
jlong JFUNC(nToInteger)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return (jlong)lua_tointeger(STATE(p), idx);
}

jdouble JFUNC(nToNumber)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return (jdouble)lua_tonumber(STATE(p), idx);
}

/* the bytes of a string or number (numbers are converted in place); null for other types and when the
   conversion runs out of memory */
jbyteArray JFUNC(nToBytes)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  lua_State *L = STATE(p);
  Op op;
  (void)cls;
  op.idx = idx;
  op.s = NULL;
  op.n = 0;
  if (lua_type(L, idx) == LUA_TNUMBER) {
    if (!guarded(L, op_tolstring, &op, 0)) return NULL;
  }
  else {
    op_tolstring(L, &op);
  }
  return op.s != NULL ? to_jbytes(env, op.s, op.n) : NULL;
}

jlong JFUNC(nRawLen)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return (jlong)lua_rawlen(STATE(p), idx);
}

jboolean JFUNC(nNewTable)(JNIEnv *env, jclass cls, jlong p) {
  Op op;
  (void)env; (void)cls;
  return guarded(STATE(p), op_newtable, &op, 0) ? JNI_TRUE : JNI_FALSE;
}

static char *name_to_c(JNIEnv *env, jbyteArray name) {
  jsize nn = (*env)->GetArrayLength(env, name);
  char *nm = (char *)malloc((size_t)nn + 1);
  if (nm == NULL) return NULL;
  (*env)->GetByteArrayRegion(env, name, 0, nn, (jbyte *)nm);
  nm[nn] = 0;
  return nm;
}

/* the type of the pushed value, or -2 when out of memory */
jint JFUNC(nRawGetField)(JNIEnv *env, jclass cls, jlong p, jint idx, jbyteArray name) {
  lua_State *L = STATE(p);
  char *nm = name_to_c(env, name);
  Op op;
  jint t = -2;
  (void)cls;
  if (nm == NULL) return -2;
  op.s = nm;
  op.idx = lua_absindex(L, idx);
  if (guarded(L, op_getfield, &op, 0)) t = op.type;
  free(nm);
  return t;
}

/* pops the value; false when out of memory (the value is popped then too) */
jboolean JFUNC(nRawSetField)(JNIEnv *env, jclass cls, jlong p, jint idx, jbyteArray name) {
  lua_State *L = STATE(p);
  char *nm = name_to_c(env, name);
  Op op;
  jboolean ok;
  (void)cls;
  if (nm == NULL) {
    lua_pop(L, 1);
    return JNI_FALSE;
  }
  op.s = nm;
  op.idx = lua_absindex(L, idx);
  ok = guarded(L, op_setfield, &op, 1) ? JNI_TRUE : JNI_FALSE;
  free(nm);
  return ok;
}

jint JFUNC(nRawGetI)(JNIEnv *env, jclass cls, jlong p, jint idx, jlong i) {
  (void)env; (void)cls;
  return lua_rawgeti(STATE(p), idx, (lua_Integer)i);
}

jboolean JFUNC(nRawSetI)(JNIEnv *env, jclass cls, jlong p, jint idx, jlong i) {
  lua_State *L = STATE(p);
  Op op;
  (void)env; (void)cls;
  op.idx = lua_absindex(L, idx);
  op.v = i;
  return guarded(L, op_seti, &op, 1) ? JNI_TRUE : JNI_FALSE;
}

/* key on top: replaces it by the value */
jint JFUNC(nRawGet)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  (void)env; (void)cls;
  return lua_rawget(STATE(p), idx);
}

/* key and value on top: removes both */
jboolean JFUNC(nRawSet)(JNIEnv *env, jclass cls, jlong p, jint idx) {
  lua_State *L = STATE(p);
  Op op;
  (void)env; (void)cls;
  op.idx = lua_absindex(L, idx);
  return guarded(L, op_rawset, &op, 2) ? JNI_TRUE : JNI_FALSE;
}

jint JFUNC(nGetGlobal)(JNIEnv *env, jclass cls, jlong p, jbyteArray name) {
  lua_State *L = STATE(p);
  char *nm = name_to_c(env, name);
  Op op;
  jint t = -2;
  (void)cls;
  if (nm == NULL) return -2;
  op.s = nm;
  if (guarded(L, op_getglobal, &op, 0)) t = op.type;
  free(nm);
  return t;
}

/* pops the value */
jboolean JFUNC(nSetGlobal)(JNIEnv *env, jclass cls, jlong p, jbyteArray name) {
  lua_State *L = STATE(p);
  char *nm = name_to_c(env, name);
  Op op;
  jboolean ok;
  (void)cls;
  if (nm == NULL) {
    lua_pop(L, 1);
    return JNI_FALSE;
  }
  op.s = nm;
  ok = guarded(L, op_setglobal, &op, 1) ? JNI_TRUE : JNI_FALSE;
  free(nm);
  return ok;
}

void JFUNC(nSetOperationLimit)(JNIEnv *env, jclass cls, jlong p, jlong limit) {
  JState *js = JS(p);
  (void)env; (void)cls;
  js->op_limit = limit > 0 ? limit : 0;
  js->op_left = js->op_limit;
  js->op_step = limit >= 10000 ? 1000 : limit >= 10 ? (int)(limit / 10) : 1;
  apply_hook(js, js->L);
}

typedef struct {
  lua_State *co;
  int ref;
} NewCo;

static void op_newcoroutine(lua_State *L, void *ud) {
  NewCo *nc = (NewCo *)ud;
  nc->co = lua_newthread(L);
  nc->ref = luaL_ref(L, LUA_REGISTRYINDEX);  /* pops the thread: the registry keeps it alive */
}

/* takes the function on top of the stack into a new coroutine; out[0] = handle, out[1] = registry reference */
jboolean JFUNC(nNewCoroutine)(JNIEnv *env, jclass cls, jlong jsp, jlong p, jlongArray out) {
  JState *js = JS(jsp);
  lua_State *L = STATE(p);
  NewCo nc;
  jlong r[2];
  int top;
  (void)cls;
  if (!lua_checkstack(L, 2) || lua_type(L, -1) != LUA_TFUNCTION) return JNI_FALSE;
  nc.co = NULL;
  nc.ref = LUA_NOREF;
  top = lua_gettop(L);
  if (wc_protect(L, op_newcoroutine, &nc) != LUA_OK || !lua_checkstack(nc.co, 1)) {
    lua_settop(L, top - 1);
    return JNI_FALSE;
  }
  lua_xmove(L, nc.co, 1);
  apply_hook(js, nc.co);
  r[0] = (jlong)(intptr_t)nc.co;
  r[1] = nc.ref;
  (*env)->SetLongArrayRegion(env, out, 0, 2, r);
  return JNI_TRUE;
}

static void op_refthread(lua_State *L, void *ud) {
  NewCo *nc = (NewCo *)ud;
  nc->ref = luaL_ref(L, LUA_REGISTRYINDEX);  /* pops the thread that was pushed above it */
}

/* pushes the coroutine that the registry reference names */
void JFUNC(nPushCoroutine)(JNIEnv *env, jclass cls, jlong p, jint ref) {
  (void)env; (void)cls;
  lua_rawgeti(STATE(p), LUA_REGISTRYINDEX, ref);
}

/* the coroutine at idx (for example one a persisted state brought back) gets a registry reference; out as in
   nNewCoroutine */
jboolean JFUNC(nToCoroutine)(JNIEnv *env, jclass cls, jlong jsp, jlong p, jint idx, jlongArray out) {
  JState *js = JS(jsp);
  lua_State *L = STATE(p);
  NewCo nc;
  jlong r[2];
  (void)cls;
  if (lua_type(L, idx) != LUA_TTHREAD || !lua_checkstack(L, 2)) return JNI_FALSE;
  nc.co = lua_tothread(L, idx);
  nc.ref = LUA_NOREF;
  lua_pushvalue(L, idx);
  if (wc_protect(L, op_refthread, &nc) != LUA_OK) {
    lua_pop(L, 1);
    return JNI_FALSE;
  }
  apply_hook(js, nc.co);
  r[0] = (jlong)(intptr_t)nc.co;
  r[1] = nc.ref;
  (*env)->SetLongArrayRegion(env, out, 0, 2, r);
  return JNI_TRUE;
}

void JFUNC(nReleaseCoroutine)(JNIEnv *env, jclass cls, jlong p, jint ref) {
  (void)env; (void)cls;
  luaL_unref(STATE(p), LUA_REGISTRYINDEX, ref);
}

/* resumes co with the nargs values on top of the stack of from; the yielded or returned values are moved to the
   stack of from. Result: status | (number of values << 8); status 0 = finished, 1 = yielded, else an error. */
jlong JFUNC(nResume)(JNIEnv *env, jclass cls, jlong jsp, jlong from, jlong co, jint nargs, jboolean reset) {
  JState *js = JS(jsp);
  lua_State *F = STATE(from);
  lua_State *C = STATE(co);
  int nres = 0;
  int status;
  (void)cls;
  js->env = env;
  if (reset) js->op_left = js->op_limit;
  if (!lua_checkstack(C, nargs + 1)) {
    lua_pop(F, nargs);
    return 100;  /* no room on a stack: nothing was resumed */
  }
  lua_xmove(F, C, nargs);
  status = lua_resume(C, F, nargs);
  if (status == LUA_OK || status == LUA_YIELD) {
    nres = lua_gettop(C);  /* a yield leaves exactly its values in the frame */
    if (!lua_checkstack(F, nres)) {
      lua_pop(C, nres);
      return 100;  /* no room on a stack: nothing was resumed */
    }
    lua_xmove(C, F, nres);
    return (jlong)status | ((jlong)nres << 8);
  }
  return status;
}

static void op_traceback(lua_State *L, void *ud) {
  Op *o = (Op *)ud;
  lua_State *C = (lua_State *)(intptr_t)o->v;
  const char *msg = lua_tostring(C, -1);
  if (msg == NULL) msg = lua_pushfstring(C, "(error object is a %s value)", luaL_typename(C, -1));
  luaL_traceback(L, C, msg, 0);
}

/* the message of the error that ended the coroutine (still on top of its stack), with a traceback of where it
   stopped; the message is popped from the coroutine */
jbyteArray JFUNC(nCoroutineError)(JNIEnv *env, jclass cls, jlong from, jlong co) {
  lua_State *F = STATE(from);
  lua_State *C = STATE(co);
  Op op;
  jbyteArray r;
  size_t n;
  const char *m;
  (void)cls;
  op.v = (jlong)(intptr_t)C;
  if (!lua_checkstack(F, 4) || !guarded(F, op_traceback, &op, 0)) {
    lua_pop(C, 1);
    return to_jbytes(env, "error", 5);
  }
  m = lua_tolstring(F, -1, &n);
  r = to_jbytes(env, m != NULL ? m : "error", m != NULL ? n : 5);
  lua_pop(F, 1);
  lua_pop(C, 1);
  return r;
}

/* --- persistence (Eris): the state of the Lua values, suspended coroutines included, as bytes */

#include "eris.h"

#define PERM_JAVA "__wc.java"

/* adds the engine's own permanents to the table on top: the Java function trampoline, the library functions */
static void add_perms(lua_State *L, int forUnpersist) {
  if (forUnpersist) {
    lua_pushstring(L, PERM_JAVA);
    lua_pushcfunction(L, java_function);
  }
  else {
    lua_pushcfunction(L, java_function);
    lua_pushstring(L, PERM_JAVA);
  }
  lua_rawset(L, -3);
  wc_populateperms(L, forUnpersist);
}

/* the permanents the caller gave (object -> name), as they are or inverted, into the table below them on top */
static void copy_perms(lua_State *L, int user, int forUnpersist) {
  int dest = lua_gettop(L);
  if (lua_type(L, user) != LUA_TTABLE) return;
  lua_pushnil(L);
  while (lua_next(L, user) != 0) {  /* key value */
    if (forUnpersist) {
      lua_pushvalue(L, -1);  /* key value value */
      lua_pushvalue(L, -3);  /* key value value key: dest[name] = object */
    }
    else {
      lua_pushvalue(L, -2);
      lua_pushvalue(L, -2);
    }
    lua_rawset(L, dest);
    lua_pop(L, 1);
  }
}

/* arguments: value, permanents or nil; returns the bytes */
static int persist_all(lua_State *L) {
  lua_settop(L, 2);
  lua_newtable(L);  /* 3: permanents */
  add_perms(L, 0);
  copy_perms(L, 2, 0);
  eris_persist(L, 3, 1);
  return 1;
}

/* arguments: bytes (light userdata, length), permanents or nil; returns the value */
static int unpersist_all(lua_State *L) {
  const char *data = (const char *)lua_touserdata(L, 1);
  size_t n = (size_t)lua_tointeger(L, 2);
  lua_settop(L, 3);
  lua_newtable(L);  /* 4: permanents */
  add_perms(L, 1);
  copy_perms(L, 3, 1);
  lua_pushlstring(L, data, n);  /* 5 */
  eris_unpersist(L, 4, 5);
  return 1;
}

/* the bytes of the value at idx, or null when it cannot be persisted (the message: nTakeError) */
jbyteArray JFUNC(nPersist)(JNIEnv *env, jclass cls, jlong jsp, jlong p, jint idx, jint permsIdx) {
  JState *js = JS(jsp);
  lua_State *L = STATE(p);
  jbyteArray r;
  size_t n;
  const char *s;
  (void)cls;
  js->env = env;
  idx = lua_absindex(L, idx);
  if (permsIdx != 0) permsIdx = lua_absindex(L, permsIdx);
  if (!lua_checkstack(L, 6)) return NULL;
  lua_pushcfunction(L, persist_all);
  lua_pushvalue(L, idx);
  if (permsIdx != 0) lua_pushvalue(L, permsIdx);
  else lua_pushnil(L);
  if (lua_pcall(L, 2, 1, 0) != LUA_OK) {
    free(js->last_error);
    s = lua_tolstring(L, -1, &n);
    js->last_error = (char *)malloc(n + 1);
    if (js->last_error != NULL) {
      memcpy(js->last_error, s != NULL ? s : "", s != NULL ? n : 0);
      js->last_error[s != NULL ? n : 0] = 0;
    }
    lua_pop(L, 1);
    return NULL;
  }
  s = lua_tolstring(L, -1, &n);
  r = to_jbytes(env, s, n);
  lua_pop(L, 1);
  return r;
}

/* pushes the value; false when it failed (see nTakeError) */
jboolean JFUNC(nUnpersist)(JNIEnv *env, jclass cls, jlong jsp, jlong p, jbyteArray data, jint permsIdx) {
  JState *js = JS(jsp);
  lua_State *L = STATE(p);
  jsize n = (*env)->GetArrayLength(env, data);
  char *buf = (char *)malloc((size_t)n + 1);
  size_t m;
  const char *s;
  int rc;
  (void)cls;
  js->env = env;
  if (buf == NULL || !lua_checkstack(L, 6)) {
    free(buf);
    return JNI_FALSE;
  }
  (*env)->GetByteArrayRegion(env, data, 0, n, (jbyte *)buf);
  if (permsIdx != 0) permsIdx = lua_absindex(L, permsIdx);
  lua_pushcfunction(L, unpersist_all);
  lua_pushlightuserdata(L, buf);
  lua_pushinteger(L, n);
  if (permsIdx != 0) lua_pushvalue(L, permsIdx);
  else lua_pushnil(L);
  rc = lua_pcall(L, 3, 1, 0);
  free(buf);
  if (rc != LUA_OK) {
    free(js->last_error);
    s = lua_tolstring(L, -1, &m);
    js->last_error = (char *)malloc(m + 1);
    if (js->last_error != NULL) {
      memcpy(js->last_error, s != NULL ? s : "", s != NULL ? m : 0);
      js->last_error[s != NULL ? m : 0] = 0;
    }
    lua_pop(L, 1);
    return JNI_FALSE;
  }
  return JNI_TRUE;
}

jbyteArray JFUNC(nTakeError)(JNIEnv *env, jclass cls, jlong jsp) {
  JState *js = JS(jsp);
  jbyteArray r;
  (void)cls;
  if (js->last_error == NULL) return to_jbytes(env, "error", 5);
  r = to_jbytes(env, js->last_error, strlen(js->last_error));
  free(js->last_error);
  js->last_error = NULL;
  return r;
}
