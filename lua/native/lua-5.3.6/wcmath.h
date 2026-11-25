/*
** The math functions the Lua core and library call all go through the names below. The ones whose results can
** differ in the last bit between C libraries and CPUs (sin, cos, tan, asin, acos, atan, atan2, sinh, cosh, tanh,
** exp, log, log2, log10, pow) and fmod are the engine's own copies of the musl / fdlibm routines (../libm), the
** same on every platform. floor, ceil, fabs, sqrt, frexp and ldexp are exact in IEEE 754 and stay with the
** platform (sqrt is one correctly rounded hardware instruction).
*/
#ifndef wcmath_h
#define wcmath_h

#include <math.h>

double wc_sin(double x);
double wc_cos(double x);
double wc_tan(double x);
double wc_asin(double x);
double wc_acos(double x);
double wc_atan(double x);
double wc_exp(double x);
double wc_log(double x);
double wc_log2(double x);
double wc_log10(double x);
double wc_sinh(double x);
double wc_cosh(double x);
double wc_tanh(double x);
double wc_atan2(double x, double y);
double wc_pow(double x, double y);
double wc_fmod(double x, double y);

#undef sin
#define sin(x) wc_sin(x)
#undef cos
#define cos(x) wc_cos(x)
#undef tan
#define tan(x) wc_tan(x)
#undef asin
#define asin(x) wc_asin(x)
#undef acos
#define acos(x) wc_acos(x)
#undef atan
#define atan(x) wc_atan(x)
#undef exp
#define exp(x) wc_exp(x)
#undef log
#define log(x) wc_log(x)
#undef log2
#define log2(x) wc_log2(x)
#undef log10
#define log10(x) wc_log10(x)
#undef sinh
#define sinh(x) wc_sinh(x)
#undef cosh
#define cosh(x) wc_cosh(x)
#undef tanh
#define tanh(x) wc_tanh(x)
#undef atan2
#define atan2(x, y) wc_atan2(x, y)
#undef pow
#define pow(x, y) wc_pow(x, y)
#undef fmod
#define fmod(x, y) wc_fmod(x, y)

#endif
