
function pipeTest () {
    cellsReset();
    mTick=0;

    let vals = [42, 3, 17]
        , pipe = new Pipe( null, {
        name: "topipe"
        , processes: [plus1, squared, negated]})
        , piperIn = new FSM( 'piperIn', null, function(ctx, is) {
        //clg('piper FSM state/mtick at entry', is, mTick);
        if ( is === 'init') {
            let v = vals.pop();
            if ( v===undefined) {
                return 'fini';
            }
            pipe.feed( v );
            clg('piperIN> fed pipe', pipe.feeder.payload);
            return 'getack';
        } else if (is === 'getack') {
            if ( pipe.feeder.ackd()) {
                //clg('DRIVER> got pipe ack');
                return 'init';
            }
        }
    })
        , piperOut = new FSM( 'piperOut', null, function(ctx, is) {
        if (is === 'init') {
            if ( pipe.out.unackd()) {
                clg('DRIVER> RESULT!!!', pipe.out.payload);
                pipe.out.ack();
                return 'init';
            }
        }
    });

    let driver = function (tick) {
        clg('DRIVER TICK', mTick = tick);
        piperIn.tick();
        pipe.tick();
        piperOut.tick();
        if ( tick < 20 ) { //|| (piperIn.state === 'fini' && pipe.out.ak > pipe.feeder.ak)) {
            setTimeout( driver, 500, tick+1);
        }
    };
    driver(1);

}

// pipeTest();


function plus1 (x) {
    return x+1;
}

function squared (x) {
    return x * x;
}

function negated (x) {
    return -x;
}

function pipeTestIII ( ) {
    cellsReset();
    mTick = 0;
    let pipe = new Pipe( null, {
        name: name
        , processes: [plus1, squared, negated]});

    ++mTick;
    clg('sbok', pipe.feed(42));

    let f7 = true;

    for (let x = 0; ++x < 30; ) {
        if (f7) {
            let f = pipe.feed(7);

            if (f === undefined) {
                clg('tester sees backp!!!!!!!!!!!!!');
            } else {
                f7 = false;
                clg("tester second feed ok", f);
            }
        }
        ++mTick;
        pipe.tick();

        let r = pipe.take();
        if ( r !== undefined) {
            clg('bam', r);
        }
    }

}

// pipeTestIII();
