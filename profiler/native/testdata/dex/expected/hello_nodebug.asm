
method Base$Inner.<init>(Base):void
{
	    0| move-object v0, v4
	    1| move-object v1, v5
	    2| move-object v2, v0
	    3| move-object v3, v1
	    4| iput-object v3, v2, Base$Inner.this$0
	    6| move-object v2, v0
	    7| invoke-direct {v2}, java.lang.Object.<init>():void
	   10| return-void
}

method Base$Nested.<init>():void
{
	    0| move-object v0, v2
	    1| move-object v1, v0
	    2| invoke-direct {v1}, java.lang.Object.<init>():void
	    5| return-void
}

method Base.<init>():void
{
	    0| move-object v0, v2
	    1| move-object v1, v0
	    2| invoke-direct {v1}, java.lang.Object.<init>():void
	    5| return-void
}

method Base.test(int):void
{
	    0| move-object v0, v6
	    1| move v1, v7
	    2| invoke-static {}, Hello.printStackTrace():void
	    5| sget-object v2, java.lang.System.out
	    7| new-instance v3, java.lang.StringBuilder
	    9| move-object v5, v3
	   10| move-object v3, v5
	   11| move-object v4, v5
	   12| invoke-direct {v4}, java.lang.StringBuilder.<init>():void
	   15| const-string v4, "Base.test "
	   17| invoke-virtual {v3,v4}, java.lang.StringBuilder.append(java.lang.String):java.lang.StringBuilder
	   20| move-result-object v3
	   21| move v4, v1
	   22| invoke-virtual {v3,v4}, java.lang.StringBuilder.append(int):java.lang.StringBuilder
	   25| move-result-object v3
	   26| invoke-virtual {v3}, java.lang.StringBuilder.toString():java.lang.String
	   29| move-result-object v3
	   30| invoke-virtual {v2,v3}, java.io.PrintStream.println(java.lang.String):void
	   33| return-void
}

method Derived.<init>():void
{
	    0| move-object v0, v2
	    1| move-object v1, v0
	    2| invoke-direct {v1}, Base.<init>():void
	    5| return-void
}

method Derived.test(int):void
{
	    0| move-object v0, v6
	    1| move v1, v7
	    2| sget-object v2, java.lang.System.out
	    4| new-instance v3, java.lang.StringBuilder
	    6| move-object v5, v3
	    7| move-object v3, v5
	    8| move-object v4, v5
	    9| invoke-direct {v4}, java.lang.StringBuilder.<init>():void
	   12| const-string v4, "Derived.test "
	   14| invoke-virtual {v3,v4}, java.lang.StringBuilder.append(java.lang.String):java.lang.StringBuilder
	   17| move-result-object v3
	   18| move v4, v1
	   19| invoke-virtual {v3,v4}, java.lang.StringBuilder.append(int):java.lang.StringBuilder
	   22| move-result-object v3
	   23| invoke-virtual {v3}, java.lang.StringBuilder.toString():java.lang.String
	   26| move-result-object v3
	   27| invoke-virtual {v2,v3}, java.io.PrintStream.println(java.lang.String):void
	   30| return-void
}

method Hello.<init>():void
{
	    0| move-object v0, v2
	    1| move-object v1, v0
	    2| invoke-direct {v1}, java.lang.Object.<init>():void
	    5| return-void
}

method Hello.main(java.lang.String[]):void
{
	    0| move-object v0, v6
	    1| sget-object v2, java.lang.System.out
	    3| const-string v3, "-------------------------------------------------------\n"
	    5| const/4 v4, #+0 (0x00000000 | 0.00000)
	    6| new-array v4, v4, java.lang.Object[]
	    8| invoke-virtual {v2,v3,v4}, java.io.PrintStream.printf(java.lang.String, java.lang.Object[]):java.io.PrintStream
	   11| move-result-object v2
	   12| sget-object v2, java.lang.System.out
	   14| const-string v3, "Hello, world (original)"
	   16| invoke-virtual {v2,v3}, java.io.PrintStream.println(java.lang.String):void
	   19| sget-object v2, java.lang.System.out
	   21| const-string v3, "-------------------------------------------------------\n"
	   23| const/4 v4, #+0 (0x00000000 | 0.00000)
	   24| new-array v4, v4, java.lang.Object[]
	   26| invoke-virtual {v2,v3,v4}, java.io.PrintStream.printf(java.lang.String, java.lang.Object[]):java.io.PrintStream
	   29| move-result-object v2
	   30| new-instance v2, Base
	   32| move-object v5, v2
	   33| move-object v2, v5
	   34| move-object v3, v5
	   35| invoke-direct {v3}, Base.<init>():void
	   38| move-object v1, v2
	   39| move-object v2, v1
	   40| const/4 v3, #+1 (0x00000001 | 1.40130e-45)
	   41| invoke-virtual {v2,v3}, Base.test(int):void
	   44| sget-object v2, java.lang.System.out
	   46| const-string v3, "-------------------------------------------------------\n"
	   48| const/4 v4, #+0 (0x00000000 | 0.00000)
	   49| new-array v4, v4, java.lang.Object[]
	   51| invoke-virtual {v2,v3,v4}, java.io.PrintStream.printf(java.lang.String, java.lang.Object[]):java.io.PrintStream
	   54| move-result-object v2
	   55| new-instance v2, Derived
	   57| move-object v5, v2
	   58| move-object v2, v5
	   59| move-object v3, v5
	   60| invoke-direct {v3}, Derived.<init>():void
	   63| move-object v1, v2
	   64| move-object v2, v1
	   65| const/4 v3, #+2 (0x00000002 | 2.80260e-45)
	   66| invoke-virtual {v2,v3}, Base.test(int):void
	   69| return-void
}

method Hello.printStackTrace():void
{
	    0| new-instance v5, java.lang.Throwable
	    2| move-object v11, v5
	    3| move-object v5, v11
	    4| move-object v6, v11
	    5| invoke-direct {v6}, java.lang.Throwable.<init>():void
	    8| invoke-virtual {v5}, java.lang.Throwable.getStackTrace():java.lang.StackTraceElement[]
	   11| move-result-object v5
	   12| move-object v0, v5
	   13| move-object v5, v0
	   14| move-object v1, v5
	   15| move-object v5, v1
	   16| array-length v5, v5
	   17| move v2, v5
	   18| const/4 v5, #+0 (0x00000000 | 0.00000)
	   19| move v3, v5
Label_1:
	   20| move v5, v3
	   21| move v6, v2
	   22| if-ge v5, v6, Label_2
	   24| move-object v5, v1
	   25| move v6, v3
	   26| aget-object v5, v5, v6
	   28| move-object v4, v5
	   29| sget-object v5, java.lang.System.out
	   31| const-string v6, "   %s\n"
	   33| const/4 v7, #+1 (0x00000001 | 1.40130e-45)
	   34| new-array v7, v7, java.lang.Object[]
	   36| move-object v11, v7
	   37| move-object v7, v11
	   38| move-object v8, v11
	   39| const/4 v9, #+0 (0x00000000 | 0.00000)
	   40| move-object v10, v4
	   41| invoke-virtual {v10}, java.lang.StackTraceElement.toString():java.lang.String
	   44| move-result-object v10
	   45| aput-object v10, v8, v9
	   47| invoke-virtual {v5,v6,v7}, java.io.PrintStream.printf(java.lang.String, java.lang.Object[]):java.io.PrintStream
	   50| move-result-object v5
	   51| add-int/lit8 v3, v3, #+1 (0x00000001 | 1.40130e-45)
	   53| goto Label_1
Label_2:
	   54| return-void
}
