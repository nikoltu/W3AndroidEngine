#include "lprefix.h"

#include <stdio.h>
#include <time.h>

#include "lua.h"
#include "lauxlib.h"
#include "lstate.h"
#include "ldo.h"

static void default_print(lua_State *L, const char *s, size_t l) {
  (void)L;
  fwrite(s, sizeof(char), l, stdout);
}

static wc_ctx default_ctx = {NULL, 0x9e3779b97f4a7c15ULL, default_print, 0, 0.0, 0};

void wc_ctx_init(wc_ctx *ctx) {
  ctx->random_hook = NULL;
  ctx->random_state = 0x9e3779b97f4a7c15ULL;
  ctx->print = default_print;
  ctx->time_seconds = 0;
  ctx->clock_seconds = 0.0;
  ctx->next_objid = 0;
}

wc_ctx *wc_getctx(lua_State *L) {
  wc_ctx *ctx = *(wc_ctx **)lua_getextraspace(L);
  return ctx != NULL ? ctx : &default_ctx;
}

/* splitmix64: small, fast and the same on every platform */
static uint64_t splitmix(uint64_t *state) {
  uint64_t z = (*state += 0x9e3779b97f4a7c15ULL);
  z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9ULL;
  z = (z ^ (z >> 27)) * 0x94d049bb133111ebULL;
  return z ^ (z >> 31);
}

uint64_t wc_random(lua_State *L) {
  wc_ctx *ctx = wc_getctx(L);
  if (ctx->random_hook != NULL)
    return ctx->random_hook(L, ctx);
  return splitmix(&ctx->random_state);
}

void wc_seed_random(lua_State *L, LUA_INTEGER seed) {
  wc_ctx *ctx = wc_getctx(L);
  if (ctx->random_hook == NULL) {
    ctx->random_state = (uint64_t)seed;
    (void)splitmix(&ctx->random_state);
  }
}

/* days from 1970-01-01 of a proleptic Gregorian date (month 1..12) */
static int64_t days_from_civil(int64_t y, int64_t m, int64_t d) {
  int64_t era, yoe, doy, doe;
  y -= m <= 2;
  era = (y >= 0 ? y : y - 399) / 400;
  yoe = y - era * 400;
  doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
  doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
  return era * 146097 + doe - 719468;
}

/* like timegm: fields out of range are normalised, no time zone is involved */
time_t wc_timegm(struct tm *tm) {
  int64_t year = (int64_t)tm->tm_year + 1900;
  int64_t month = tm->tm_mon;
  int64_t days;
  year += month / 12;
  month %= 12;
  if (month < 0) {
    month += 12;
    year -= 1;
  }
  days = days_from_civil(year, month + 1, 1) + (tm->tm_mday - 1);
  return (time_t)(days * 86400 + (int64_t)tm->tm_hour * 3600 + (int64_t)tm->tm_min * 60 + tm->tm_sec);
}

typedef struct { wc_pfn f; void *ud; } ProtectData;

static void protect_call (lua_State *L, void *ud) {
  ProtectData *pd = (ProtectData *)ud;
  pd->f(L, pd->ud);
}

int wc_protect (lua_State *L, wc_pfn f, void *ud) {
  ProtectData pd;
  pd.f = f;
  pd.ud = ud;
  return luaD_rawrunprotected(L, protect_call, &pd);
}

/* WC-PERSIST: the names of the functions of the loaded standard libraries ("__wc.lib.<library>.<function>") */
static void permfunctions(lua_State *L, int perms, const char *libname, int forUnpersist) {
  int lib = lua_gettop(L);
  lua_pushnil(L);
  while (lua_next(L, lib) != 0) {
    if (lua_iscfunction(L, -1) && lua_type(L, -2) == LUA_TSTRING) {
      if (forUnpersist) {
        lua_pushfstring(L, "__wc.lib.%s.%s", libname, lua_tostring(L, -2));  /* key value name */
        lua_pushvalue(L, -2);
        lua_rawset(L, perms);
      }
      else {
        /* a name the caller gave the function stays: a script may keep a native under another global */
        lua_pushvalue(L, -1);
        if (lua_rawget(L, perms) == LUA_TNIL) {
          lua_pop(L, 1);
          lua_pushvalue(L, -1);
          lua_pushfstring(L, "__wc.lib.%s.%s", libname, lua_tostring(L, -3));  /* key value value name */
          lua_rawset(L, perms);
        }
        else {
          lua_pop(L, 1);
        }
      }
    }
    lua_pop(L, 1);
  }
}

void wc_populateperms (lua_State *L, int forUnpersist) {
  static const char *const libs[] = {"_G", "coroutine", "table", "string", "math", "utf8", "os", "debug", NULL};
  int perms = lua_gettop(L);
  int i;
  luaL_checkstack(L, 8, NULL);
  wc_permbaselib(L, forUnpersist);
  wc_permcorolib(L, forUnpersist);
  wc_permstrlib(L, forUnpersist);
  if (lua_getfield(L, LUA_REGISTRYINDEX, "_LOADED") != LUA_TTABLE) {
    lua_pop(L, 1);
    return;
  }
  for (i = 0; libs[i] != NULL; i++) {
    if (lua_getfield(L, -1, libs[i]) == LUA_TTABLE) permfunctions(L, perms, libs[i], forUnpersist);
    lua_pop(L, 1);
  }
  lua_pop(L, 1);
}
