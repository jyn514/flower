(defn kbd [keys]
   (str "<kbd>"
        (sed
          (sed keys " " "</kbd><kbd>") "+" " + ") "</kbd>"))
