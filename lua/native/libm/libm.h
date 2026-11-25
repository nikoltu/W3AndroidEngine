/*
** Shim for the musl 1.1.24 math sources in this directory (MIT, see COPYRIGHT.musl; the algorithms are the
** FreeBSD msun / Sun fdlibm and ARM optimized-routines ones). The engine uses them on every platform so that
** the scripts' sin, cos, exp, ... give the same bits everywhere: the public functions are renamed wc_*, and
** the fused multiply-add paths are off, because they would give other bits on the CPUs that have the instruction.
*/
#ifndef _LIBM_H
#define _LIBM_H

#include <stdint.h>
#include <float.h>
#include <math.h>
#include <stddef.h>

#if defined(__BYTE_ORDER__) && __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
#error The math sources assume a little-endian target
#endif
#if defined(__FP_FAST_FMA)
#undef __FP_FAST_FMA
#endif
#define __FP_FAST_FMA 0
#define hidden
#define weak

#define sin wc_sin
#define cos wc_cos
#define tan wc_tan
#define asin wc_asin
#define acos wc_acos
#define atan wc_atan
#define atan2 wc_atan2
#define exp wc_exp
#define log wc_log
#define log2 wc_log2
#define log10 wc_log10
#define pow wc_pow
#define sinh wc_sinh
#define cosh wc_cosh
#define tanh wc_tanh
#define expm1 wc_expm1
#define fmod wc_fmod
#define scalbn wc_scalbn
#define __sin wc___sin
#define __cos wc___cos
#define __tan wc___tan
#define __rem_pio2 wc___rem_pio2
#define __rem_pio2_large wc___rem_pio2_large
#define __expo2 wc___expo2
#define __math_xflow wc___math_xflow
#define __math_uflow wc___math_uflow
#define __math_oflow wc___math_oflow
#define __math_divzero wc___math_divzero
#define __math_invalid wc___math_invalid
#define __exp_data wc___exp_data
#define __log_data wc___log_data
#define __log2_data wc___log2_data
#define __pow_log_data wc___pow_log_data

double wc_sin(double), wc_cos(double), wc_tan(double), wc_asin(double), wc_acos(double), wc_atan(double);
double wc_exp(double), wc_log(double), wc_log2(double), wc_log10(double);
double wc_sinh(double), wc_cosh(double), wc_tanh(double), wc_expm1(double);
double wc_atan2(double, double), wc_pow(double, double), wc_fmod(double, double);
double wc_scalbn(double, int);

/* Support non-nearest rounding mode.  */
#define WANT_ROUNDING 1
/* Support signaling NaNs.  */
#define WANT_SNAN 0

#if WANT_SNAN
#error SNaN is unsupported
#else
#define issignalingf_inline(x) 0
#define issignaling_inline(x) 0
#endif

#ifndef TOINT_INTRINSICS
#define TOINT_INTRINSICS 0
#endif

#if TOINT_INTRINSICS
/* Round x to nearest int in all rounding modes, ties have to be rounded
   consistently with converttoint so the results match.  If the result
   would be outside of [-2^31, 2^31-1] then the semantics is unspecified.  */
static double_t roundtoint(double_t);

/* Convert x to nearest int in all rounding modes, ties have to be rounded
   consistently with roundtoint.  If the result is not representible in an
   int32_t then the semantics is unspecified.  */
static int32_t converttoint(double_t);
#endif

/* Helps static branch prediction so hot path can be better optimized.  */
#ifdef __GNUC__
#define predict_true(x) __builtin_expect(!!(x), 1)
#define predict_false(x) __builtin_expect(x, 0)
#else
#define predict_true(x) (x)
#define predict_false(x) (x)
#endif

/* Evaluate an expression as the specified type. With standard excess
   precision handling a type cast or assignment is enough (with
   -ffloat-store an assignment is required, in old compilers argument
   passing and return statement may not drop excess precision).  */

static inline float eval_as_float(float x)
{
	float y = x;
	return y;
}

static inline double eval_as_double(double x)
{
	double y = x;
	return y;
}

/* fp_barrier returns its input, but limits code transformations
   as if it had a side-effect (e.g. observable io) and returned
   an arbitrary value.  */

#ifndef fp_barrierf
#define fp_barrierf fp_barrierf
static inline float fp_barrierf(float x)
{
	volatile float y = x;
	return y;
}
#endif

#ifndef fp_barrier
#define fp_barrier fp_barrier
static inline double fp_barrier(double x)
{
	volatile double y = x;
	return y;
}
#endif

#ifndef fp_barrierl
#define fp_barrierl fp_barrierl
static inline long double fp_barrierl(long double x)
{
	volatile long double y = x;
	return y;
}
#endif

/* fp_force_eval ensures that the input value is computed when that's
   otherwise unused.  To prevent the constant folding of the input
   expression, an additional fp_barrier may be needed or a compilation
   mode that does so (e.g. -frounding-math in gcc). Then it can be
   used to evaluate an expression for its fenv side-effects only.   */

#ifndef fp_force_evalf
#define fp_force_evalf fp_force_evalf
static inline void fp_force_evalf(float x)
{
	volatile float y;
	y = x;
}
#endif

#ifndef fp_force_eval
#define fp_force_eval fp_force_eval
static inline void fp_force_eval(double x)
{
	volatile double y;
	y = x;
}
#endif

#ifndef fp_force_evall
#define fp_force_evall fp_force_evall
static inline void fp_force_evall(long double x)
{
	volatile long double y;
	y = x;
}
#endif

#define FORCE_EVAL(x) do {                        \
	if (sizeof(x) == sizeof(float)) {         \
		fp_force_evalf(x);                \
	} else if (sizeof(x) == sizeof(double)) { \
		fp_force_eval(x);                 \
	} else {                                  \
		fp_force_evall(x);                \
	}                                         \
} while(0)

#define asuint(f) ((union{float _f; uint32_t _i;}){f})._i
#define asfloat(i) ((union{uint32_t _i; float _f;}){i})._f
#define asuint64(f) ((union{double _f; uint64_t _i;}){f})._i
#define asdouble(i) ((union{uint64_t _i; double _f;}){i})._f

#define EXTRACT_WORDS(hi,lo,d)                    \
do {                                              \
  uint64_t __u = asuint64(d);                     \
  (hi) = __u >> 32;                               \
  (lo) = (uint32_t)__u;                           \
} while (0)

#define GET_HIGH_WORD(hi,d)                       \
do {                                              \
  (hi) = asuint64(d) >> 32;                       \
} while (0)

#define GET_LOW_WORD(lo,d)                        \
do {                                              \
  (lo) = (uint32_t)asuint64(d);                   \
} while (0)

#define INSERT_WORDS(d,hi,lo)                     \
do {                                              \
  (d) = asdouble(((uint64_t)(hi)<<32) | (uint32_t)(lo)); \
} while (0)

#define SET_HIGH_WORD(d,hi)                       \
  INSERT_WORDS(d, hi, (uint32_t)asuint64(d))

#define SET_LOW_WORD(d,lo)                        \
  INSERT_WORDS(d, asuint64(d)>>32, lo)

#define GET_FLOAT_WORD(w,d)                       \
do {                                              \
  (w) = asuint(d);                                \
} while (0)

#define SET_FLOAT_WORD(d,w)                       \
do {                                              \
  (d) = asfloat(w);                               \
} while (0)


hidden int    __rem_pio2_large(double*,double*,int,int,int);
hidden int    __rem_pio2(double,double*);
hidden double __sin(double,double,int);
hidden double __cos(double,double);
hidden double __tan(double,double,int);
hidden double __expo2(double);

/* error handling functions */
hidden float __math_xflowf(uint32_t, float);
hidden float __math_uflowf(uint32_t);
hidden float __math_oflowf(uint32_t);
hidden float __math_divzerof(uint32_t);
hidden float __math_invalidf(float);
hidden double __math_xflow(uint32_t, double);
hidden double __math_uflow(uint32_t);
hidden double __math_oflow(uint32_t);
hidden double __math_divzero(uint32_t);
hidden double __math_invalid(double);

#endif
