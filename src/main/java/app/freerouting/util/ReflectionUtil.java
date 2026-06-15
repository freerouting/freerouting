package app.freerouting.util;

import app.freerouting.logger.FRLogger;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ReflectionUtil {

  private ReflectionUtil() {
  }

  public static void setFieldValue(Object obj, String propertyName, Object newValue)
      throws Exception {
    String[] propertyPath = propertyName.split("[.:\\-]");
    setPropertyRecursive(obj, propertyPath, 0, newValue);
  }

  private static void setPropertyRecursive(Object currentObject, String[] propertyPath, int pathIndex, Object newValue)
      throws Exception {
    if (currentObject == null) {
      return;
    }

    String currentName = propertyPath[pathIndex];
    Field field = getFieldByNameOrSerializedName(currentObject.getClass(), currentName);
    field.setAccessible(true);

    if (pathIndex == propertyPath.length - 1) {
      // Leaf field - set the value
      Object convertedValue = convertValue(field.getType(), newValue);
      field.set(currentObject, convertedValue);
      return;
    }

    // Intermediate field - check if it is an array
    if (field.getType().isArray()) {
      Class<?> componentType = field.getType().getComponentType();
      // We are navigating through an array. The next part of the path is a property of the array element.
      // E.g., layers.routable
      // The newValue is expected to be a comma-separated list of values for each array element.
      String[] valTokens = newValue.toString().split(",");
      int size = valTokens.length;

      Object array = field.get(currentObject);
      if (array == null) {
        array = java.lang.reflect.Array.newInstance(componentType, size);
        field.set(currentObject, array);
      }

      int arrayLength = java.lang.reflect.Array.getLength(array);
      int limit = Math.min(arrayLength, size);

      for (int i = 0; i < limit; i++) {
        Object element = java.lang.reflect.Array.get(array, i);
        if (element == null) {
          element = componentType.getDeclaredConstructor().newInstance();
          java.lang.reflect.Array.set(array, i, element);
        }
        // Recursively set the property on the array element
        setPropertyRecursive(element, propertyPath, pathIndex + 1, valTokens[i].trim());
      }
    } else {
      // Normal object navigation
      Object nestedObject = field.get(currentObject);
      if (nestedObject == null) {
        nestedObject = field.getType().getDeclaredConstructor().newInstance();
        field.set(currentObject, nestedObject);
      }
      setPropertyRecursive(nestedObject, propertyPath, pathIndex + 1, newValue);
    }
  }

  private static Field getFieldByNameOrSerializedName(Class<?> clazz, String name) throws NoSuchFieldException {
    for (Field field : clazz.getDeclaredFields()) {
      SerializedName annotation = field.getAnnotation(SerializedName.class);
      if (annotation != null && annotation.value().equals(name)) {
        return field;
      }
      if (field.getName().equals(name)) {
        // Enforce that fields with a SerializedName must not be queried by their camelCase Java name
        if (annotation != null && !name.equals(name.toLowerCase())) {
          continue;
        }
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
    if (targetType == String[].class) {
      // Parse a comma-separated string into a String array.
      // This allows CLI arguments and environment variables to supply list values,
      // e.g. --api_server-endpoints=http://0.0.0.0:37864,http://127.0.0.1:37864
      String raw = value.toString().trim();
      if (raw.isEmpty()) {
        return new String[0];
      }
      String[] tokens = raw.split(",");
      for (int i = 0; i < tokens.length; i++) {
        tokens[i] = tokens[i].trim();
      }
      return tokens;
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

        // For nullable wrappers (Boolean/Integer/...), non-null already means "explicitly set".
        // Keep default-value suppression only for primitive fields.
        boolean shouldCopy = sourceValue != null;
        if (shouldCopy && field.getType().isPrimitive()) {
          shouldCopy = !sourceValue.equals(getDefaultValue(field));
        }

        if (shouldCopy) {
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
              // The field is an array of objects (like LayerSettings[])
              Object[] sourceArray = (Object[]) sourceValue;
              Class<?> componentType = field.getType().getComponentType();

              Object targetArrayObj = field.get(target);
              int targetLength = targetArrayObj != null ? java.lang.reflect.Array.getLength(targetArrayObj) : 0;

              if (targetLength >= sourceArray.length) {
                // Merge source elements into existing target elements
                Object[] targetObjArray = (Object[]) targetArrayObj;
                for (int i = 0; i < sourceArray.length; i++) {
                  if (sourceArray[i] != null) {
                    if (targetObjArray[i] == null) {
                      targetObjArray[i] = componentType.getDeclaredConstructor().newInstance();
                    }
                    copyFields(sourceArray[i], targetObjArray[i]);
                  }
                }
                numberOfFieldsChanged += sourceArray.length;
              } else {
                // Allocate a new target array of the specific component type
                Object[] targetArray = (Object[]) java.lang.reflect.Array.newInstance(componentType, sourceArray.length);
                for (int i = 0; i < sourceArray.length; i++) {
                  if (sourceArray[i] != null) {
                    Object targetElement = componentType.getDeclaredConstructor().newInstance();
                    copyFields(sourceArray[i], targetElement);
                    targetArray[i] = targetElement;
                  }
                }
                field.set(target, targetArray);
                numberOfFieldsChanged += sourceArray.length;
              }
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
        FRLogger.debug("No default constructor found for field: " + field.getName());
      }
    } catch (Exception e) {
      FRLogger.error("Error getting default value for field: " + field.getName(), e);
    }

    return result;
  }
}
