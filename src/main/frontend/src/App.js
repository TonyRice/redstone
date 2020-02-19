import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { Navbar, Nav, Container } from "react-bootstrap";
import "./App.css";
import Routes from "./Routes";
import { LinkContainer } from 'react-router-bootstrap'


function App(props) {
  const [isAuthenticated, userHasAuthenticated] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(true);

  async function handleLogout() {
    let resp = await fetch('/v1/logout');

    if (resp.status === 200) {
      userHasAuthenticated(false);
    }
  }

  useEffect(() => {
    onLoad();
  }, []);

  async function onLoad() {
    try {
      let resp = await fetch('/v1/auth');

      console.log(resp.status === 200);
      if (resp.status === 200) {
        userHasAuthenticated(true);

      }
    }
    catch (e) {
      if (e !== 'No current user') {
        alert(e);
      }
    }

    setIsAuthenticating(false);
  }

  return (
    !isAuthenticating &&
    <div className="App container">
      <Navbar bg="light" expand="lg">
        <Nav className="ml-auto">
          <Nav.Item>
            <LinkContainer to="/">
              <Nav.Link>Home</Nav.Link>
            </LinkContainer>
          </Nav.Item>
        </Nav>
        <Navbar.Toggle className="ml-auto" />

        <Navbar.Collapse >

          <Nav className="ml-auto">
            {isAuthenticated
              ? <>
                <Nav.Item>
                  <Nav.Link onClick={handleLogout}>Logout</Nav.Link>
                </Nav.Item>
              </>
              : <>
                <Nav.Item>
                  <LinkContainer to="/login">

                    <Nav.Link>Login</Nav.Link>
                  </LinkContainer>
                </Nav.Item>
              </>
            }
          </Nav>
        </Navbar.Collapse>
      </Navbar>
      <Routes appProps={{ isAuthenticated, userHasAuthenticated }} />

    </div>
  );
}

export default App;
