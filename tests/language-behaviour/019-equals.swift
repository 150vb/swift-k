type messagefile {}

(messagefile t) greeting(int m) { 
    app {
        echo m stdout=@filename(t);
    }
}

messagefile outfile <"019-equals.out">;

int i = 7==9;

outfile = greeting(i);

