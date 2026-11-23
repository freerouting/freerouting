using System;
using System.Collections.Generic;
using System.Reflection;
using RT_PCB;

namespace Freerouting.Altium.Core.Tests
{
    public class AltiumProxy<T> : DispatchProxy where T : class
    {
        public Dictionary<string, Func<object[], object>> Handlers { get; } = new Dictionary<string, Func<object[], object>>(StringComparer.OrdinalIgnoreCase);

        protected override object Invoke(MethodInfo targetMethod, object[] args)
        {
            if (Handlers.TryGetValue(targetMethod.Name, out var handler))
            {
                return handler(args);
            }
            if (targetMethod.ReturnType == typeof(string)) return "";
            if (targetMethod.ReturnType == typeof(int)) return 0;
            if (targetMethod.ReturnType == typeof(bool)) return false;
            if (targetMethod.ReturnType == typeof(double)) return 0.0;
            if (targetMethod.ReturnType == typeof(IntPtr)) return IntPtr.Zero;
            if (targetMethod.ReturnType == typeof(void)) return null;
            if (targetMethod.ReturnType.IsValueType) return Activator.CreateInstance(targetMethod.ReturnType);
            return null;
        }

        public static T Create(Action<Dictionary<string, Func<object[], object>>> configure = null)
        {
            object proxy = DispatchProxy.Create<T, AltiumProxy<T>>();
            var handlers = ((AltiumProxy<T>)proxy).Handlers;
            configure?.Invoke(handlers);
            return (T)proxy;
        }
    }
}
