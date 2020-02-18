import React, { useState, useEffect } from "react";
import { ListGroup } from "react-bootstrap";
import "./Home.css";

export default function Home(props) {
  const [wires, setWires] = useState([]);

  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    async function onLoad() {
      if (!props.isAuthenticated) {
        return;
      }

      try {
        const wires = await loadWires();
        setWires(wires);
      } catch (e) {
        console.error(e);
      }

      setIsLoading(false);
    }

    onLoad();
  }, [props.isAuthenticated]);

  function loadWires() {
    return fetch('/v1/wires').then((resp) => {
      return resp.json();
    }).then((body) => {
      return body;
    })
  }
  function activateWire(wireId) {
    return fetch('/v1/wires/' + wireId + "/activate").then(async (r) => {
      if (r.status === 200) {
        const wires = await loadWires();
        setWires(wires);
      }
    });
  }

  function renderLander() {
    return (
      <div className="lander">
        <h1>Redstone</h1>
        <p>Simple automated workflows powered by JSON, and HTTP.</p>
      </div>
    );
  }

  function renderWires() {
    return (
      <div className="wires">
        <ListGroup>
          {!isLoading && wires.map((wire) => {
            if (wire.active == true) {
              return (
                <ListGroup.Item key={wire.id} onClick={() => activateWire(wire.id)} active action>{wire.title}</ListGroup.Item>)
            }
            return (
              <ListGroup.Item key={wire.id} onClick={() => activateWire(wire.id)} action>{wire.title}</ListGroup.Item>
            );
          })}
        </ListGroup>
      </div>
    );
  }

  return (
    <div className="Home">
      {props.isAuthenticated ? renderWires() : renderLander()}
    </div>
  );
}