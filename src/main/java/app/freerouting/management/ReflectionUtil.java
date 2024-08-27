package app.freerouting.management;

import app.freerouting.logger.FRLogger;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Field;

public class ReflectionUtil
{

  public static void setFieldValue(Object obj, String propertyName, Object newValue) throws NoSuchFieldException, IllegalAccessException
  {
    String[] propertyPath = propertyName.split("[.:\\-]");
    Object currentObject = obj;
    Field field = null;

    // Navigate to the nested object
    for (int i = 0; i < propertyPath.length - 1; i++)
    {
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

  private static Field getFieldByNameOrSerializedName(Class<?> clazz, String name) throws NoSuchFieldException
  {
    for (Field field : clazz.getDeclaredFields())
    {
      if (field.getName().equals(name))
      {
        return field;
      }
      SerializedName annotation = field.getAnnotation(SerializedName.class);
      if (annotation != null && annotation.value().equals(name))
      {
        return field;
      }
    }
    throw new NoSuchFieldException("No field found with name or SerializedName: " + name);
  }

  private static Object convertValue(Class<?> targetType, Object value)
  {
    if (targetType.isInstance(value))
    {
      return value;
    }
    if (targetType == int.class || targetType == Integer.class)
    {
      return Integer.parseInt(value.toString());
    }
    if (targetType == long.class || targetType == Long.class)
    {
      return Long.parseLong(value.toString());
    }
    if (targetType == double.class || targetType == Double.class)
    {
      return Double.parseDouble(value.toString());
    }
    if (targetType == boolean.class || targetType == Boolean.class)
    {
      // convert "0" and "1" into their boolean values
      if (value.toString().equals("0"))
      {
        value = "false";
      }
      else if (value.toString().equals("1"))
      {
        value = "true";
      }

      return Boolean.parseBoolean(value.toString());
    }
    // Add more type conversions as needed
    return value;
  }

  /* Copy all non-null fields from one object to another recursively */
  public static void copyFields(Object source, Object target)
  {
    for (Field field : source.getClass().getDeclaredFields())
    {
      try
      {
        // check if the field is static and skip it if it is
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()))
        {
          continue;
        }

        // check if the field is private and skip it if it is
        if (!java.lang.reflect.Modifier.isPublic(field.getModifiers()))
        {
          continue;
        }

        field.setAccessible(true);
        Object value = field.get(source);
        if (value != null)
        {
          // Check if the field is a primitive or a string
          if (field.getType().isPrimitive() || field.getType() == String.class)
          {
            field.set(target, value);
          }
          else
            // Check if the field is an enum
            if (field.getType().isEnum())
            {
              // Copy the enum value
              field.set(target, Enum.valueOf((Class<Enum>) field.getType(), value.toString()));
            }
            else
              // Check if the field is an array
              if (field.getType().isArray())
              {


                // Is the array of primitive types or strings?
                if (field.getType().getComponentType().isPrimitive() || field.getType().getComponentType() == String.class)
                {
                  // Only set the field if it is not null on the source object
                  Object targetValue = field.get(target);

                  if (targetValue == null)
                  {
                    // The field is an array of primitive types or strings, so we can copy it directly
                    field.set(target, value);
                  }
                }
                else
                {
                  // The field is an array, so we need to copy its elements
                  Object[] sourceArray = (Object[]) value;
                  Object[] targetArray = (Object[]) field.get(target);
                  if (targetArray == null)
                  {
                    targetArray = new Object[sourceArray.length];
                    field.set(target, targetArray);
                  }
                  System.arraycopy(sourceArray, 0, targetArray, 0, sourceArray.length);
                }
              }
              else
              {
                // The field is an object, so we need to copy its fields
                Object targetField = field.get(target);
                if (targetField == null)
                {
                  targetField = field.getType().newInstance();
                  field.set(target, targetField);
                }
                copyFields(value, targetField);
              }
        }
      } catch (Exception e)
      {
        FRLogger.error("Error copying fields", e);
      }

    }

  }
}