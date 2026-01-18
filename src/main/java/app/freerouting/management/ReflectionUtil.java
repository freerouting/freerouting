package app.freerouting.management;

import app.freerouting.logger.FRLogger;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionUtil {

  private ReflectionUtil() {
  }

  public static void setFieldValue(Object obj, String propertyName, Object newValue)
      throws NoSuchFieldException, IllegalAccessException {
    String[] propertyPath = propertyName.split("[.:\\-]");
    Object currentObject = obj;
    Field field = null;

    // Navigate to the nested object
    for (int i = 0; i < propertyPath.length - 1; i++) {
      field = getFieldByNameOrSerializedName(currentObject.getClass(), propertyPath[i]);
      field.setAccessible(true);
      currentObject = field.get(currentObject);
    }

    // Set the final field value
    field = getFieldByNameOrSerializedName(currentObject.getClass(), propertyPath[propertyPath.length - 1]);
    field.setAccessible(true);
    Object convertedValue = convertValue(field.getType(), newValue);
    field.set(currentObject, convertedValue);
  }

  private static Field getFieldByNameOrSerializedName(Class<?> clazz, String name) throws NoSuchFieldException {
    for (Field field : clazz.getDeclaredFields()) {
      if (field
          .getName()
          .equals(name)) {
        return field;
      }
      SerializedName annotation = field.getAnnotation(SerializedName.class);
      if (annotation != null && annotation
          .value()
          .equals(name)) {
        return field;
      }
    }
    throw new NoSuchFieldException("No field found with name or SerializedName: " + name);
  }

  private static Object convertValue(Class<?> targetType, Object value) {
    if (targetType.isInstance(value)) {
      return value;
    }
    if (targetType == int.class || targetType == Integer.class) {
      return Integer.parseInt(value.toString());
    }
    if (targetType == long.class || targetType == Long.class) {
      return Long.parseLong(value.toString());
    }
    if (targetType == double.class || targetType == Double.class) {
      return Double.parseDouble(value.toString());
    }
    if (targetType == boolean.class || targetType == Boolean.class) {
      // convert "0" and "1" into their boolean values
      if ("0"
          .equals(value
              .toString())) {
        value = "false";
      } else if ("1"
          .equals(value
              .toString())) {
        value = "true";
      }

      return Boolean.parseBoolean(value.toString());
    }
    // Add more type conversions as needed
    return value;
  }

  /**
   * Copy all non-null, and non-default fields from one object to another
   * recursively
   *
   * @param source The source object
   * @param target The target object
   * @return The number of fields that were copied
   */
  @SuppressWarnings("unchecked")
  public static int copyFields(Object source, Object target) {
    int numberOfFieldsChanged = 0;

    for (Field field : source
        .getClass()
        .getDeclaredFields()) {
      try {
        // check if the field is static and skip it if it is
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }

        // check if the field is private and skip it if it is
        if (!Modifier.isPublic(field.getModifiers())) {
          continue;
        }

        field.setAccessible(true);
        Object sourceValue = field.get(source);

        // Only copy the field if the new value is not null, and not the default value
        if ((sourceValue != null) && !sourceValue.equals(getDefaultValue(field))) {
          // Check if the field is a primitive, wrapper type, or a string
          if (field.getType().isPrimitive()
              || field.getType() == String.class
              || field.getType() == Integer.class
              || field.getType() == Long.class
              || field.getType() == Float.class
              || field.getType() == Double.class
              || field.getType() == Boolean.class
              || field.getType() == Byte.class
              || field.getType() == Short.class
              || field.getType() == Character.class) {
            // check if the target field is null or its default value
            var targetValue = field.get(target);

            // if ((targetValue == null) || targetValue.equals(getDefaultValue(field)))
            if (targetValue != sourceValue) {
              field.set(target, sourceValue);
              numberOfFieldsChanged++;
            }
          } else
          // Check if the field is an enum
          if (field
              .getType()
              .isEnum()) {
            var enumType = (Class<Enum>) field.getType();
            var enumValue = Enum.valueOf(enumType, sourceValue.toString());

            // Copy the enum value
            field.set(target, enumValue);
            numberOfFieldsChanged++;
          } else
          // Check if the field is an array
          if (field
              .getType()
              .isArray()) {

            // Is the array of primitive types or strings?
            if (field
                .getType()
                .getComponentType()
                .isPrimitive()
                || field
                    .getType()
                    .getComponentType() == String.class) {
              // Only set the field if it is not null on the source object
              Object targetValue = field.get(target);

              int targetArrayLength = 0;
              if (targetValue != null && targetValue
                  .getClass()
                  .isArray()) {
                targetArrayLength = java.lang.reflect.Array.getLength(targetValue);
              }

              int sourceArrayLength = 0;
              if (sourceValue != null && sourceValue
                  .getClass()
                  .isArray()) {
                sourceArrayLength = java.lang.reflect.Array.getLength(sourceValue);
              }

              // Check if the target field is null or its length is 0
              if ((targetValue == null) || ((targetArrayLength == 0) && (sourceArrayLength > 0))) {
                // The field is an array of primitive types or strings, so we can copy it
                // directly
                field.set(target, sourceValue);
                numberOfFieldsChanged++;
              }
            } else {
              // The field is an array, so we need to copy its elements
              Object[] sourceArray = (Object[]) sourceValue;
              Object[] targetArray = (Object[]) field.get(target);
              if (targetArray == null) {
                targetArray = new Object[sourceArray.length];
                field.set(target, targetArray);
              }
              System.arraycopy(sourceArray, 0, targetArray, 0, sourceArray.length);
              numberOfFieldsChanged += sourceArray.length;
            }
          } else {
            // The field is an object, so we need to copy its fields
            Object targetField = field.get(target);
            if (targetField == null) {
              targetField = field
                  .getType()
                  .getDeclaredConstructor()
                  .newInstance();
              field.set(target, targetField);
            }
            numberOfFieldsChanged += copyFields(sourceValue, targetField);
          }
        }
      } catch (Exception e) {
        FRLogger.error("Error copying fields", e);
      }

    }

    return numberOfFieldsChanged;
  }

  private static Object getDefaultValue(Field field) {
    Object result = null;

    try {
      result = field
          .getType()
          .getConstructor()
          .newInstance();
    } catch (NoSuchMethodException _) {
      // The field does not have a default constructor, this can usually the case if
      // the type is a primitive type
      if (field.getType() == int.class || field.getType() == Integer.class) {
        result = 0;
      } else if (field.getType() == long.class || field.getType() == Long.class) {
        result = 0L;
      } else if (field.getType() == float.class || field.getType() == Float.class) {
        result = 0.0f;
      } else if (field.getType() == double.class || field.getType() == Double.class) {
        result = 0.0;
      } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
        result = false;
      } else if (field
          .getType()
          .isArray()) {
        // create an empty array of the original type
        result = java.lang.reflect.Array.newInstance(field
            .getType()
            .getComponentType(), 0);
      } else if (field
          .getType()
          .isEnum()) {
        result = field
            .getType()
            .getEnumConstants()[0];
      } else if (Modifier.isTransient(field.getModifiers())) {
        result = null;
      } else {
        FRLogger.warn("No default constructor found for field: " + field.getName());
      }
    } catch (Exception e) {
      FRLogger.error("Error getting default value for field: " + field.getName(), e);
    }

    return result;
  }
}