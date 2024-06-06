; ModuleID = 'module'
source_filename = "module"

define i32 @main() {
mainEntry:
  %a = alloca i32, align 4
  store i32 0, i32* %a, align 4
  %count = alloca i32, align 4
  store i32 0, i32* %count, align 4
  br label %whileCondition

whileCondition:                                   ; preds = %mainEntry
  %a1 = load i32, i32* %a, align 4
  %tmp_ = icmp sle i32 %a1, 0
  %tmp_2 = zext i1 %tmp_ to i32
  %tmp_3 = icmp ne i32 0, %tmp_2
  br i1 %tmp_3, label %whileBody, label %entry

whileBody:                                        ; preds = %entry10, %whileCondition
  %a4 = load i32, i32* %a, align 4
  store i32 %a4, i32* %a, align 4
  %count5 = load i32, i32* %count, align 4
  store i32 %count5, i32* %count, align 4
  %a6 = load i32, i32* %a, align 4
  %tmp_7 = icmp slt i32 %a6, -20
  %tmp_8 = zext i1 %tmp_7 to i32
  %tmp_9 = icmp ne i32 0, %tmp_8
  br i1 %tmp_9, label %true, label %false

entry:                                            ; preds = %entry10, %true, %whileCondition
  %count15 = load i32, i32* %count, align 4
  ret i32 %count15

true:                                             ; preds = %whileBody
  br label %entry
  br label %entry10

false:                                            ; preds = %whileBody
  br label %entry10

entry10:                                          ; preds = %false, %true
  %a11 = load i32, i32* %a, align 4
  %tmp_12 = icmp sle i32 %a11, 0
  %tmp_13 = zext i1 %tmp_12 to i32
  %tmp_14 = icmp ne i32 0, %tmp_13
  br i1 %tmp_14, label %whileBody, label %entry
}
