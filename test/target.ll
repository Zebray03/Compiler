; ModuleID = 'module'
source_filename = "module"

@a = global i32 10

define i32 @main() {
mainEntry:
  %a = load i32, i32* @a, align 4
  %tmp_ = icmp ne i32 %a, 10
  %tmp_1 = zext i1 %tmp_ to i32
  %tmp_2 = icmp ne i32 0, %tmp_1
  %lhs = zext i1 %tmp_2 to i32
  %tmp_3 = icmp ne i32 0, %lhs
  br i1 %tmp_3, label %lhsIsTrue, label %lhsIsFalse

lhsIsTrue:                                        ; preds = %mainEntry
  br label %entry

lhsIsFalse:                                       ; preds = %mainEntry
  %a4 = load i32, i32* @a, align 4
  %tmp_5 = icmp ne i32 %a4, 20
  %tmp_6 = zext i1 %tmp_5 to i32
  %tmp_7 = icmp ne i32 0, %tmp_6
  %lhs8 = zext i1 %tmp_3 to i32
  %rhs = zext i1 %tmp_7 to i32
  br label %entry

entry:                                            ; preds = %lhsIsFalse, %lhsIsTrue
  %tmp_9 = or i32 %lhs8, %rhs
  %tmp_10 = icmp ne i32 0, %tmp_9
  %_tmp = zext i1 %tmp_10 to i32
  %ifCondition = icmp ne i32 0, %_tmp
  br i1 %ifCondition, label %ifConditionIsTrue, label %ifConditionIsFalse

ifConditionIsTrue:                                ; preds = %entry
  store i32 2, i32* @a, align 4
  br label %entry11

ifConditionIsFalse:                               ; preds = %entry
  store i32 20, i32* @a, align 4
  br label %entry11

entry11:                                          ; preds = %ifConditionIsFalse, %ifConditionIsTrue
  %a12 = load i32, i32* @a, align 4
  ret i32 %a12
}
