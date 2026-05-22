package io.github.scala_tessella.editor.operations

import munit.FunSuite

/** Pure unit tests for the version-comparison logic. The fetch / Var / DOM-listener side of `UpdateChecker`
  * is not exercised here; the Playwright smoke suite covers the integrated banner flow.
  */
class UpdateCheckerSpec extends FunSuite:

  test("isNewer returns true when the candidate's patch component is greater") {
    assert(UpdateChecker.isNewer("0.3.7", "0.3.8"))
  }

  test("isNewer returns true when the candidate's minor component is greater") {
    assert(UpdateChecker.isNewer("0.3.7", "0.4.0"))
  }

  test("isNewer returns true when the candidate's major component is greater") {
    assert(UpdateChecker.isNewer("0.3.7", "1.0.0"))
  }

  test("isNewer returns false for the same version") {
    assert(!UpdateChecker.isNewer("0.3.7", "0.3.7"))
  }

  test("isNewer returns false when the candidate is older") {
    assert(!UpdateChecker.isNewer("0.3.7", "0.3.6"))
    assert(!UpdateChecker.isNewer("0.3.7", "0.2.99"))
    assert(!UpdateChecker.isNewer("1.0.0", "0.99.99"))
  }

  test("isNewer treats trailing-zero variants as equal (1.2 vs 1.2.0)") {
    assert(!UpdateChecker.isNewer("1.2", "1.2.0"))
    assert(!UpdateChecker.isNewer("1.2.0", "1.2"))
  }

  test("isNewer returns true when the candidate has a higher higher-level segment despite shorter form") {
    // 1.3 > 1.2.99
    assert(UpdateChecker.isNewer("1.2.99", "1.3"))
  }

  test("isNewer compares numerically, not lexically") {
    // String compare would say "0.3.10" < "0.3.7"; numeric compare says it's newer.
    assert(UpdateChecker.isNewer("0.3.7", "0.3.10"))
    assert(!UpdateChecker.isNewer("0.3.10", "0.3.7"))
  }

  test("isNewer handles double-digit segments") {
    assert(UpdateChecker.isNewer("0.9.0", "0.10.0"))
    assert(UpdateChecker.isNewer("9.9.9", "10.0.0"))
  }

  test("isNewer is robust to malformed candidate (non-numeric segment becomes 0)") {
    // Non-numeric segments demote to 0, so a typo on the server can't force a banner.
    assert(!UpdateChecker.isNewer("0.3.7", "garbage"))
    assert(!UpdateChecker.isNewer("0.3.7", "0.3.x"))
    // But a valid prefix still wins where it should.
    assert(UpdateChecker.isNewer("0.3.7", "0.4.x"))
  }

  test("isNewer strips SemVer pre-release suffixes — `-rc.1` reads as the eventual release") {
    // 0.3.7-rc.1 should not nag a user running 0.3.7 to reload to a same-version RC.
    assert(UpdateChecker.isNewer("0.3.7", "0.4.0-rc.1"))
    assert(!UpdateChecker.isNewer("0.3.7", "0.3.7-rc.1"))
  }
