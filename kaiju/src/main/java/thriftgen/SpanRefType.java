/**
 * Autogenerated by Thrift Compiler (0.12.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package thriftgen;


@javax.annotation.Generated(value = "Autogenerated by Thrift Compiler (0.12.0)", date = "2019-02-11")
public enum SpanRefType implements org.apache.thrift.TEnum {
  CHILD_OF(0),
  FOLLOWS_FROM(1);

  private final int value;

  private SpanRefType(int value) {
    this.value = value;
  }

  /**
   * Get the integer value of this enum value, as defined in the Thrift IDL.
   */
  public int getValue() {
    return value;
  }

  /**
   * Find a the enum type by its integer value, as defined in the Thrift IDL.
   * @return null if the value is not found.
   */
  @org.apache.thrift.annotation.Nullable
  public static SpanRefType findByValue(int value) { 
    switch (value) {
      case 0:
        return CHILD_OF;
      case 1:
        return FOLLOWS_FROM;
      default:
        return null;
    }
  }
}