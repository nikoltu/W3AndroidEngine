/*
** Per-state context of the Warsmash patches: the engine's hooks for the random numbers,
** the clock and the output, which replace the sources of non-determinism of the stock
** library. The context is found through the extra space of the state's threads.
*/
#ifndef wcdet_h
#define wcdet_h

#include <stddef.h>
#include <stdint.h>
#include <time.h>

#include "wcmath.h"

struct lua_State;
struct tm;

typedef struct wc_ctx {
  /* next 64 random bits from the engine, or NULL for the built-in deterministic generator */
  uint64_t (*random_hook)(struct lua_State *L, struct wc_ctx *ctx);
  uint64_t random_state;
  /* output of print */
  void (*print)(struct lua_State *L, const char *s, size_t l);
  /* os.time() / os.clock() */
  LUA_INTEGER time_seconds;
  double clock_seconds;
  /* last id given to a printed table, function or userdata */
  LUA_INTEGER next_objid;
} wc_ctx;

/* Division of floats as the game does it: a divisor of zero gives 2^127 with the sign of the quotient, and 0/0 gives 1. */
static inline LUA_NUMBER wc_numdiv(LUA_NUMBER a, LUA_NUMBER b) {
  if (b == 0) {
    if (a != a) return a;
    if (a == 0) return (LUA_NUMBER)1;
    return (signbit(a) != signbit(b)) ? (LUA_NUMBER)-1.70141183460469231731687303715884105728e38
                                      : (LUA_NUMBER)1.70141183460469231731687303715884105728e38;
  }
  return a / b;
}

/* the context of a state; a shared default one for states that were not given one */
wc_ctx *wc_getctx(struct lua_State *L);
void wc_ctx_init(wc_ctx *ctx);
uint64_t wc_random(struct lua_State *L);
void wc_seed_random(struct lua_State *L, LUA_INTEGER seed);
time_t wc_timegm(struct tm *tm);

/* Runs f(L, ud) so that a Lua error raised inside (out of memory in a push, for example) comes back as the status
   instead of unwinding to whatever handler is installed, which for the bridge may be a Java frame or none at all. */
typedef void (*wc_pfn)(struct lua_State *L, void *ud);
int wc_protect(struct lua_State *L, wc_pfn f, void *ud);

/* Fills the table on top of the stack for Eris: object -> name when persisting, name -> object when unpersisting, for
   the functions of the standard libraries and the C functions that coroutines can be suspended inside. */
void wc_populateperms(struct lua_State *L, int forUnpersist);
void wc_permbaselib(struct lua_State *L, int forUnpersist);
void wc_permcorolib(struct lua_State *L, int forUnpersist);
void wc_permstrlib(struct lua_State *L, int forUnpersist);

/* print writes through the context (lauxlib.h only defines these when they are missing) */
#define lua_writestring(s,l) (wc_getctx(L)->print(L, (s), (l)))
#define lua_writeline() lua_writestring("\n", 1)

#endif
