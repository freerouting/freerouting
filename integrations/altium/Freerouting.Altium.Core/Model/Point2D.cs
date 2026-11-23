namespace Freerouting.Altium.Core.Model
{
    /// <summary>
    /// Represents a 2D coordinate point in Altium internal integer units (1 mil = 10,000 units).
    /// </summary>
    public struct Point2D
    {
        public int X { get; private set; }
        public int Y { get; private set; }

        public Point2D(int x, int y) : this()
        {
            X = x;
            Y = y;
        }

        public long DistanceSquaredTo(Point2D other)
        {
            long dx = (long)X - other.X;
            long dy = (long)Y - other.Y;
            return dx * dx + dy * dy;
        }

        public override string ToString()
        {
            return string.Format("({0}, {1})", X, Y);
        }
    }
}
