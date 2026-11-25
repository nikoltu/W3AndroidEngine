/*
** Prints (gen) or verifies (check <file>) the bits of the engine's math routines for a fixed input set. The same
** file must verify on every platform; the unit tests run the same inputs through Lua.
** build: cc -O2 -ffp-contract=off -I../libm libmcheck.c ../libm/*.c -o libmcheck
*/
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

double wc_sin(double), wc_cos(double), wc_tan(double), wc_asin(double), wc_acos(double), wc_atan(double);
double wc_exp(double), wc_log(double), wc_log2(double), wc_log10(double), wc_sinh(double), wc_cosh(double), wc_tanh(double);
double wc_atan2(double, double), wc_pow(double, double), wc_fmod(double, double);

typedef struct { const char *name; int two; double (*f1)(double); double (*f2)(double, double); double range; } Fn;
static const Fn fns[] = {
  {"sin", 0, wc_sin, 0, 1e3}, {"cos", 0, wc_cos, 0, 1e3}, {"tan", 0, wc_tan, 0, 1e3},
  {"asin", 0, wc_asin, 0, 1.0}, {"acos", 0, wc_acos, 0, 1.0}, {"atan", 0, wc_atan, 0, 1e3},
  {"exp", 0, wc_exp, 0, 50}, {"log", 0, wc_log, 0, 1e6}, {"log2", 0, wc_log2, 0, 1e6}, {"log10", 0, wc_log10, 0, 1e6},
  {"sinh", 0, wc_sinh, 0, 20}, {"cosh", 0, wc_cosh, 0, 20}, {"tanh", 0, wc_tanh, 0, 10},
  {"atan2", 1, 0, wc_atan2, 100}, {"pow", 1, 0, wc_pow, 20}, {"fmod", 1, 0, wc_fmod, 1e4},
};

static uint64_t sm;
static uint64_t next(void) {
  uint64_t z = (sm += 0x9E3779B97F4A7C15ULL);
  z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ULL;
  z = (z ^ (z >> 27)) * 0x94D049BB133111EBULL;
  return z ^ (z >> 31);
}
static double bits2d(uint64_t b) { double d; memcpy(&d, &b, 8); return d; }
static uint64_t d2bits(double d) { uint64_t b; memcpy(&b, &d, 8); return b; }
static double unit(void) { return (double)(next() >> 11) / 9007199254740992.0 * 2.0 - 1.0; }

/* kind 0: uniform in [-range, range]; 1: wide exponent; 2: special values */
static double input(const Fn *f, int i, int arg) {
  static const double special[] = {0.0, -0.0, 1.0, -1.0, 0.5, 2.0, 3.141592653589793, 1e-300, 1e300, 710.0, -745.0, 1e22, 0.1, 10.0, 1.0 / 0.0, -1.0 / 0.0};
  if (i < 16) return special[(i + arg * 5) % 16];
  if (i % 3 == 0) {
    double d = bits2d(next() & 0x7FFFFFFFFFFFFFFFULL | ((next() & 1) << 63));
    int e = (int)((d2bits(d) >> 52) & 0x7FF);
    if (e > 1023 + 40 || e < 1023 - 60) return unit() * f->range;
    return d;
  }
  return unit() * f->range;
}

int main(int argc, char **argv) {
  int check = argc > 2 && !strcmp(argv[1], "check");
  FILE *in = check ? fopen(argv[2], "r") : NULL;
  long bad = 0, n = 0;
  size_t k;
  if (check && !in) { fprintf(stderr, "no file\n"); return 2; }
  for (k = 0; k < sizeof fns / sizeof fns[0]; k++) {
    int i;
    sm = 0x1234567ULL + k;
    for (i = 0; i < 300; i++) {
      double a = input(&fns[k], i, 0), b = fns[k].two ? input(&fns[k], i, 1) : 0, r;
      uint64_t rb;
      r = fns[k].two ? fns[k].f2(a, b) : fns[k].f1(a);
      rb = isnan(r) ? 0x7FF8000000000000ULL : d2bits(r);
      if (!check) {
        printf("%s %016llx %016llx %016llx\n", fns[k].name, (unsigned long long)d2bits(a), (unsigned long long)d2bits(b), (unsigned long long)rb);
      } else {
        char name[16]; unsigned long long x, y, z;
        if (fscanf(in, "%15s %llx %llx %llx", name, &x, &y, &z) != 4) { fprintf(stderr, "short file\n"); return 2; }
        n++;
        if (strcmp(name, fns[k].name) || x != d2bits(a) || y != d2bits(b) || z != rb) {
          if (bad++ < 10) fprintf(stderr, "MISMATCH %s %016llx %016llx got %016llx want %016llx\n", fns[k].name, x, y, (unsigned long long)rb, z);
        }
      }
    }
  }
  if (check) { printf("checked %ld mismatches %ld\n", n, bad); return bad != 0; }
  return 0;
}
