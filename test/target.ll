
define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  %b = alloca i32, align 4
  %b1 = load i32, i32* %b, align 4
  %a2 = load i32, i32* %a, align 4
  %tmp_ = add i32 %a2, 2
  store i32 %tmp_, i32 %b1, align 4
  %a3 = load i32, i32* %a, align 4
  ret i32 %a3
}


define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  %b = alloca i32, align 4
  %b1 = load i32, i32* %b, align 4
  %a2 = load i32, i32* %a, align 4
  %tmp_ = add i32 %a2, 2
  store i32 %tmp_, i32 %b1, align 4
  %a3 = load i32, i32* %a, align 4
  ret i32 %a3
}


define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  %b = alloca i32, align 4
  %b1 = load i32, i32* %b, align 4
  %a2 = load i32, i32* %a, align 4
  %tmp_ = add i32 %a2, 2
  store i32 %tmp_, i32 %b1, align 4
  %a3 = load i32, i32* %a, align 4
  ret i32 %a3
}

define i32 @main() {
  %1 = alloca i32, align 4
  %2 = alloca i32, align 4
  %4 = load i32, i32* %1
  %5 = add i32 %4, 2

  store i32 %5, i32* %2
  %6 = load i32, i32* %1
  ret i32 %6
}