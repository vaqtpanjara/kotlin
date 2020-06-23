val o = object : Iterable<Int> {
    override fun iterator()<hint text=": Iterator<Int>" /> = object : Iterator<Int> {
        override fun next()<hint text=": Int" /> = 1
        override fun hasNext()<hint text=": Boolean" /> = true
    }
}
